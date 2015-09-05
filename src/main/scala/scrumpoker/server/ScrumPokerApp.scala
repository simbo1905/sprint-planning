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
import org.mashupbots.socko.events.{WebSocketFrameEvent, HttpResponseStatus}
import org.mashupbots.socko.handlers.StaticContentHandler
import org.mashupbots.socko.handlers.StaticContentHandlerConfig
import org.mashupbots.socko.handlers.StaticFileRequest
import org.mashupbots.socko.infrastructure.Logger
import org.mashupbots.socko.routes._
import org.mashupbots.socko.webserver.WebServer
import org.mashupbots.socko.webserver.WebServerConfig
import org.mashupbots.socko.webserver.WebSocketConnections
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSelection.toScala
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import scrumpoker.game._
import akka.actor.ActorPath
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory
import io.netty.handler.codec.http.HttpContent
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Try, Success, Failure}
import scrumpoker.game.Polling
import scrumpoker.game.Registration
import scrumpoker.game.Close
import scrumpoker.game.Close
import org.mashupbots.socko.webserver.WebLogConfig
import io.netty.handler.codec.http.HttpHeaders

// TODO class is too big with too many imports it needs to be broken up
object ScrumGameApp extends Logger with SnowflakeIds {

  // should be a socko util function?
  def releaseFrameAfterUse(f: WebSocketFrameEvent)( work: => Unit ) = {
    Try {
      work
    } match {
      case _ => f.wsFrame.release()
    }
  }

  val actorSystem = ActorSystem("ScrumPokerActorSystem", ConfigFactory.parseString(actorConfig))

  val staticContentHandlerRouter = actorSystem.actorOf(Props(new StaticContentHandler(staticContentHandlerConfig)).withDispatcher("static-pinned-dispatcher"), "static-file-router")

  /**
   * Command line options are interface/ip to bind to, web port to bind to, and optional websockets port and fallback port for polling
   * On openshift you run the app bound on port 8080, yet websockets most connect back on port 8000 and if the browser needs to poll it does so on the standard port 80
   */
  def main(args: Array[String]) {

    val commandLineMap = (for ((v, i) <- args.zipWithIndex) yield (i, v)).toMap
    val interface = commandLineMap.getOrElse(0, "localhost")
    val bindPort = commandLineMap.getOrElse(1, "8080").toInt
    val websocketPort = commandLineMap.getOrElse(2, bindPort.toString).toInt
    val fallbackPort = commandLineMap.getOrElse(3, bindPort.toString).toInt

    val processInfoTempFile = createProcessInfoTempFile(websocketPort, fallbackPort)

    def scrumGame = actorSystem.actorSelection("/user/scrumGame")

    val routes = Routes({

      /**
       * Websocket handshake
       */
      case wh @ WebSocketHandshake(wsHandshake) => wsHandshake match {
        case GET(PathSegments("websocket" :: roomNumber :: player :: Nil)) => {
          val playerIdOpt: Option[Long] = player.toLongOpt
          playerIdOpt match {
            case None =>
              log.warn(s"Could not parse $player as long in handsake $wsHandshake")
            case Some(playerId) =>
              log.info(s"Websocket handshake for player $playerId to join room $roomNumber")
              wsHandshake.authorize(onComplete = Some((webSocketId: String) => {
                log.info(s"Authorised connection for roomNumber:$roomNumber, playerId:$playerId, webSocketId:$webSocketId")
                scrumGame ! Registration(roomNumber, playerId, Websocket(webSocketId))
              }), onClose = Some((webSocketId: String) => {
                scrumGame ! Closed(Websocket(webSocketId))
              }))
          }
        }
        case unknown => log.error(s"could not match wsHandshake contained in $wh")
      }

      /**
       * Websocket inbound message
       */
      case f@WebSocketFrame(wsFrame) => {
        releaseFrameAfterUse(f)  {
          log.debug("ws data from:" + wsFrame.webSocketId);
          wsFrame.endPoint.pathSegments match {
            case "websocket" :: roomNumber :: playerId :: Nil if roomNumber != "" && playerId != "" =>
              scrumGame ! Data(roomNumber, wsFrame.readText())
            case _ =>
              log.warn(s"invalid wsFrame endpoint: ${wsFrame.endPoint}")
              None
          }
        }
      }

      case r @ HttpRequest(httpRequest) =>
        implicit val event = httpRequest
        httpRequest match {

          /**
           * AJAX inbound handler receiving posts from jquery.gracefulWebSocket.js
           * Registers the user with the room on the first post which should be 'fallback'
           */
          case POST(PathSegments("websocket" :: roomNumber :: player :: Nil)) =>
            player.toLongOpt match {
              case None => log.warn(s"$player is not a long in post of $httpRequest")
              case Some(playerId) =>
                if (httpRequest.nettyHttpRequest.isInstanceOf[HttpContent]) {
                  val content = httpRequest.nettyHttpRequest.asInstanceOf[HttpContent].content()
                  if (content.isReadable) {
                    val message: String = content.toString(java.nio.charset.Charset.forName("UTF8"))
                    if (message == "handshake") {
                      log.info(s"Fallback handshake for player $playerId to join room $roomNumber")
                      scrumGame ! Registration(roomNumber, playerId, Polling(player))
                      httpRequest.response.write(tsJson())
                    } else {
                      log.debug(s"$player -> $roomNumber -> $message")
                      scrumGame ! Data(roomNumber, message)
                      val future = scrumGame ? PollRequest(player)
                      future onComplete {
                        case Success(result) => result match {
                          case r: PollResponse => httpRequest.response.write(r.toJson)
                          case x => log.error(s"unknown response $x")
                        }
                        case Failure(failure) =>
                          log.info(s"polling failure for player:${player} in roomNumber:{roomNumber} with failure: ${failure.getMessage()}")
                          httpRequest.response.write(closeJson)
                      }
                    }
                  } else {
                    log.warn(s"ByteBuf content is not readable from post with headers:${httpRequest}")
                  }
                  content.release()
                } else {
                  log.warn(s"nettyHttpRequest is not a HttpContent request with headers:${httpRequest}")
                }
            }

          /**
           * AJAX outbound handler receiving polls from jquery.gracefulWebSocket.js
           */
          case GET(PathSegments("websocket" :: roomNumber :: player :: Nil)) =>
            val future = scrumGame ? PollRequest(player)
            future onComplete {
              case Success(result) => result match {
                case r: PollResponse => httpRequest.response.write(r.toJson)
                case Close => httpRequest.response.write(closeJson)
                case x => log.error(s"unknown response $x")
              }
              case Failure(failure) => httpRequest.response.write(errorJson(failure))
            }

          /**
           *  Send registering players to the main page encoding the room and their player id
           *  Firefox and android2.3.3 seem to cache a redirect so trying 303 response rather than 302
           */
          case GET(PathSegments("register" :: "redirect" :: Nil)) =>
            val player = nextId()
            val page = httpRequest.endPoint.getQueryString("skin").getOrElse("poker.html")
            val room = httpRequest.endPoint.getQueryString("room").getOrElse("-1")
            val url = s"/${page}?room=${room}&player=${player}"
            httpRequest.response.headers.put(HttpHeaders.Names.LOCATION, url)
            httpRequest.response.write(HttpResponseStatus.SEE_OTHER)

          /**
           * Openshift currently requires the use of a high port for websockets so we need the browser to check that this is reachable if behind a corporate firewall
           */
          case GET(PathSegments("websocket" :: "ports" :: Nil)) =>
            staticContentHandlerRouter ! new StaticFileRequest(httpRequest, processInfoTempFile)

          /**
           * Server static content out of contentDir defaulting to index.html and giving a 404 for favicon.ico
           */
          case Path("/") =>
            staticContentHandlerRouter ! new StaticFileRequest(httpRequest, new File(contentDir, "index.html"))
          case Path("/favicon.ico") =>
            httpRequest.response.write(HttpResponseStatus.NOT_FOUND)
          case GET(PathSegments(fileName :: Nil)) =>
            staticContentHandlerRouter ! new StaticFileRequest(httpRequest, new File(contentDir, fileName))
          case GET(PathSegments(List(folderName, fileName))) =>
            staticContentHandlerRouter ! fileInFolderRequest(folderName, fileName)

          case unknown =>
            log.debug(s"could not match $httpRequest contained in $r")
            httpRequest.response.headers.put(HttpHeaders.Names.LOCATION, "/index.html")
            httpRequest.response.write(HttpResponseStatus.MOVED_PERMANENTLY)
        }

      case unknown => log.warn(s"could not match ${unknown.getClass().getName()} = ${unknown}")
    })

    val webServer: WebServer = new WebServer(WebServerConfig(hostname = interface, port = bindPort, webLog = Some(WebLogConfig())), routes, actorSystem)
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run {
        webServer.stop()
        actorSystem.shutdown()
      }
    })

    actorSystem.actorOf(Props(classOf[ScrumGameActor], webServer.webSocketConnections), "scrumGame");

    webServer.start()

    System.out.println(s"Serving web content out of ${contentPath}")
    System.out.println(s"Open a few browsers and navigate to http://${interface}:${bindPort}. Start playing!")
  }
}

object HttpDataFactory {
  val value = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE)
}