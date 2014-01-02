//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package scrumpoker.server

import java.io.File
import scala.Array.canBuildFrom
import org.mashupbots.socko.events.HttpResponseStatus
import org.mashupbots.socko.handlers.StaticContentHandler
import org.mashupbots.socko.handlers.StaticContentHandlerConfig
import org.mashupbots.socko.handlers.StaticFileRequest
import org.mashupbots.socko.infrastructure.Logger
import org.mashupbots.socko.routes.GET
import org.mashupbots.socko.routes.HttpRequest
import org.mashupbots.socko.routes.Path
import org.mashupbots.socko.routes.PathSegments
import org.mashupbots.socko.routes.Routes
import org.mashupbots.socko.routes.WebSocketFrame
import org.mashupbots.socko.routes.WebSocketHandshake
import org.mashupbots.socko.webserver.WebServer
import org.mashupbots.socko.webserver.WebServerConfig
import org.mashupbots.socko.webserver.WebSocketConnections
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSelection.toScala
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import scrumpoker.game.Data
import scrumpoker.game.Registration
import scrumpoker.game.ScrumGameActor
import scrumpoker.game.Initialize

object ScrumGameApp extends Logger with SnowflakeIds {

  val actorConfig = """
	static-pinned-dispatcher {
	  type=PinnedDispatcher
	  executor=thread-pool-executor
	}
	akka {
	  event-handlers = ["akka.event.slf4j.Slf4jEventHandler"]
      event-handler-startup-timeout = 60s
	  loglevel=DEBUG
	  actor {
	    deployment {
	      /static-file-router {
	        router = round-robin
	        nr-of-instances = 25
	      }
	    }
	  }
	}
  """

  val actorSystem = ActorSystem("ScrumPokerActorSystem", ConfigFactory.parseString(actorConfig))

  val scrumGame = actorSystem.actorOf(Props[ScrumGameActor], "scrumGame");

  val contentPath = scala.util.Properties.envOrElse("SP_HTML_CONTENT_PATH", "src/main/resources")
  val contentDir = new File(contentPath);
  val staticContentHandlerConfig = StaticContentHandlerConfig(
    rootFilePaths = Seq(contentDir.getAbsolutePath))

  val staticContentHandlerRouter = actorSystem.actorOf(Props(new StaticContentHandler(staticContentHandlerConfig)).withDispatcher("static-pinned-dispatcher"), "static-file-router")

  /**
   * Command line options are interface/ip to bind to, web port to bind to, and optional websockets port [for openshift]
   */
  def main(args: Array[String]) {
    val clm = (for ((v, i) <- args.zipWithIndex) yield (i, v)).toMap
    val h = clm.getOrElse(0, "localhost")
    val p = clm.getOrElse(1, "8080").toInt
    val ws = clm.getOrElse(2, "8080").toInt

    val routes = Routes({

      case r @ HttpRequest(httpRequest) => httpRequest match {
        case Path("/") => {
          staticContentHandlerRouter ! new StaticFileRequest(httpRequest, new File(contentDir, "index.html"))
        }
        case Path("/favicon.ico") => {
          httpRequest.response.write(HttpResponseStatus.NOT_FOUND)
        }
        case GET(PathSegments(fileName :: Nil)) => {
          staticContentHandlerRouter ! new StaticFileRequest(httpRequest, new File(contentDir, fileName))
        }
        case GET(PathSegments("register" :: "redirect" :: Nil)) => {
          val page = httpRequest.endPoint.getQueryString("skin").getOrElse("poker.html")
          val room = httpRequest.endPoint.getQueryString("room").getOrElse("-1")
          val player = nextId()
          httpRequest.response.redirect(s"/${page}?room=${room}&player=${player}&port=${ws}")
        }
        case unknown => log.error(s"could not match $httpRequest contained in $r")
      }

      case wh @ WebSocketHandshake(wsHandshake) => wsHandshake match { // TODO: onClose
        case GET(PathSegments("websocket" :: roomNumber :: player :: Nil)) => {
          val playerIdOpt: Option[Long] = player.toLongOpt
          playerIdOpt match {
            case None =>
            case Some(playerId) =>
              log.info("Handshake to join room " + roomNumber)
              wsHandshake.authorize(onComplete = Some((webSocketId: String) => {
                log.info(s"Authorised connection for roomNumber:$roomNumber, playerId:$playerId, webSocketId:$webSocketId");
                scrumGame ! Registration(roomNumber, playerId, webSocketId);
              }), onClose = None)
          }
        }
        case unknown => log.error(s"could not match wsHandshake contained in $wh")
      }

      case WebSocketFrame(wsFrame) => {
        // Once handshaking has taken place, we can now process frames sent from the client
        log.debug("chat from:" + wsFrame.webSocketId);

        wsFrame.endPoint.pathSegments match {
          case "websocket" :: roomNumber :: playerId :: Nil if roomNumber != "" && playerId != "" =>
            val game = actorSystem.actorSelection("/user/scrumGame")
            game ! Data(roomNumber, wsFrame.readText())
          case _ =>
            log.warn(s"invalid wsFrame endpoint: ${wsFrame.endPoint}")
            None
        }
      }

      case unknown => log.error(s"could not match ${unknown.getClass().getName()} = ${unknown}")
    })

    val webServer = new WebServer(WebServerConfig(hostname = h, port = p), routes, actorSystem)
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run {
        webServer.stop()
        actorSystem.shutdown()
      }
    })

    val connections: WebSocketConnections = webServer.webSocketConnections
    scrumGame ! Initialize(connections)
    webServer.start()

    if (p != ws) {
      // openshift requires second port for the websockets
      System.out.println(s"first server is on ${p} starting as second server out on ${ws}")
      val webServer2 = new WebServer(WebServerConfig(hostname = h, port = p), routes, actorSystem)
      Runtime.getRuntime.addShutdownHook(new Thread {
        override def run {
          webServer.stop()
          actorSystem.shutdown()
        }
      })
      webServer2.start()
    }

    System.out.println(s"Serving web content out of ${contentPath}")
    System.out.println(s"Open a few browsers and navigate to http://${h}:${p}. Start playing!")
  }

}
