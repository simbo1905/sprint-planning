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
import org.mashupbots.socko.events.HttpResponseStatus
import org.mashupbots.socko.events.WebSocketHandshakeEvent
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
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import scrumpoker.game.{ScrumGameActor, Registration, Data}

object ScrumGameApp extends Logger with SnowflakeIds {

  import WebSocketExtensions._;

  val actorConfig = """
	static-pinned-dispatcher {
	  type=PinnedDispatcher
	  executor=thread-pool-executor
	}
	akka {
	  event-handlers = ["akka.event.slf4j.Slf4jEventHandler"]
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

  val routes = Routes({

    case HttpRequest(httpRequest) => httpRequest match {
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
        httpRequest.response.redirect(s"/${page}?room=${room}&player=${player}")
      }
    }

    case WebSocketHandshake(wsHandshake) => wsHandshake match {
      case GET(PathSegments("websocket" :: roomNumber :: Nil)) => {
        log.info("Handshake to join room " + roomNumber)
        wsHandshake.authorize(onComplete = Some((event: WebSocketHandshakeEvent) => {
          val secWebSocketKey = event.secWebSocketKey;
          log.info("Authorised connection:" + secWebSocketKey);
          scrumGame ! Registration(event.channel, roomNumber, secWebSocketKey);
        }))
      }
    }

    case WebSocketFrame(wsFrame) => {
      // Once handshaking has taken place, we can now process frames sent from the client
      val webSocketKey = wsFrame.secWebSocketKey;
      log.debug("chat from:" + webSocketKey);

      val optRoomNumber = wsFrame.endPoint.pathSegments match {
        case "websocket" :: roomNumber :: Nil => Some(roomNumber)
        case _ => None
      }

      val game = actorSystem.actorSelection("/user/scrumGame")
      game ! Data(optRoomNumber.getOrElse("None"), wsFrame.readText())
    }

  })

  def main(args: Array[String]) {
    val clm = ( for( (v,i) <- args.zipWithIndex ) yield (i,v) ).toMap
    val h = clm.getOrElse(0,"localhost")
    val p = clm.getOrElse(1,"8080").toInt
    val webServer = new WebServer(WebServerConfig(hostname=h,port=p), routes, actorSystem)
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run {
        webServer.stop()
        actorSystem.shutdown()
      }
    })
    webServer.start()

    System.out.println(s"Serving web content out of ${contentPath}")
    System.out.println(s"Open a few browsers and navigate to http://${h}:${p}. Start playing!")
  }

}
