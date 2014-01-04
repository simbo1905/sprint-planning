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
import org.mashupbots.socko.routes.POST
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
import akka.pattern.ask
import scrumpoker.game._
import akka.actor.ActorPath
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory
import io.netty.handler.codec.http.HttpContent
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success
import scala.util.Failure
import scrumpoker.game.Polling
import scrumpoker.game.Registration
import scrumpoker.game.Close
import scrumpoker.game.Close

// TODO class is too big with too many imports it needs to be broken up
object ScrumGameApp extends Logger with SnowflakeIds {

  val actorSystem = ActorSystem("ScrumPokerActorSystem", ConfigFactory.parseString(actorConfig))

  val staticContentHandlerRouter = actorSystem.actorOf(Props(new StaticContentHandler(staticContentHandlerConfig)).withDispatcher("static-pinned-dispatcher"), "static-file-router")

  /**
   * Command line options are interface/ip to bind to, web port to bind to, and optional websockets port and fallback port for polling
   * On openshift you run the app bound on port 8080, yet websockets most connect back on port 8000 and if the browser needs to poll it does so on the standard port 80
   */
  def main(args: Array[String]) {

    val commandLineMap = (for ((v, i) <- args.zipWithIndex) yield (i, v)).toMap
    val interface = commandLineMap.getOrElse(0, "localhost")
    val staticPort = commandLineMap.getOrElse(1, "8080").toInt
    val websocketPort = commandLineMap.getOrElse(2, "8080").toInt
    val fallbackPort = commandLineMap.getOrElse(3, "8080").toInt

    def scrumGame = actorSystem.actorSelection("/user/scrumGame")

    val routes = Routes({ // TODO break this massive block up into orElse chains

      case r @ HttpRequest(httpRequest) => httpRequest match {
        case Path("/") =>
          staticContentHandlerRouter ! new StaticFileRequest(httpRequest, new File(contentDir, "index.html"))

        case Path("/favicon.ico") =>
          httpRequest.response.write(HttpResponseStatus.NOT_FOUND)

        case GET(PathSegments(fileName :: Nil)) =>
          staticContentHandlerRouter ! new StaticFileRequest(httpRequest, new File(contentDir, fileName))

        case GET(PathSegments("register" :: "redirect" :: Nil)) =>
          val player = nextId()
          val page = httpRequest.endPoint.getQueryString("skin").getOrElse("poker.html")
          val room = httpRequest.endPoint.getQueryString("room").getOrElse("-1")
          val fallback = httpRequest.endPoint.getQueryString("fallback").getOrElse("false")

          val port = fallback match {
            case "true" => fallbackPort
            case _ => websocketPort
          }

          val redirect = s"/${page}?room=${room}&player=${player}&port=${port}"

          fallback match {
            case "true" =>
              // websockets not enabled on the browser to register this player as a polling connection using the http port
              scrumGame ! Registration(room, player, Polling(player.toString))
              log.debug(s"polling client redirecting to: $redirect")
            case _ =>
              log.debug(s"websocket client redirecting to: $redirect")
          }

          httpRequest.response.redirect(redirect)

        case POST(PathSegments("websocket" :: roomNumber :: player :: Nil)) =>
          val decoder = new HttpPostRequestDecoder(HttpDataFactory.value, httpRequest.nettyHttpRequest)
          if (httpRequest.nettyHttpRequest.isInstanceOf[HttpContent]) {
            val content = httpRequest.nettyHttpRequest.asInstanceOf[HttpContent].content()
            if (content.isReadable) {
              val message: String = content.toString(java.nio.charset.Charset.forName("UTF8"))
              log.debug(s"$player -> $roomNumber -> $message")
              scrumGame ! Data(roomNumber, message)
              val future = scrumGame ? PollRequest(player)
              future onComplete {
                case Success(result) => result match {
                  case r: PollResponse => httpRequest.response.write(r.toJson)
                  case x => log.error(s"unknown response $x")
                }
                case Failure(failure) => httpRequest.response.write(errorJson(failure))
              }
            } else {
              log.warn(s"ByteBuf content is not readable from post with headers:${httpRequest}")
            }
            content.release()
          } else {
            log.warn(s"nettyHttpRequest is not a HttpContent request with headers:${httpRequest}")
          }

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

        case unknown => log.error(s"could not match $httpRequest contained in $r")
      }

      case wh @ WebSocketHandshake(wsHandshake) => wsHandshake match {
        case GET(PathSegments("websocket" :: roomNumber :: player :: Nil)) => {
          val playerIdOpt: Option[Long] = player.toLongOpt
          playerIdOpt match {
            case None => log.warn(s"Could not parse $player as long in handsake $wsHandshake")
            case Some(playerId) =>
              log.info(s"Handshake for player $playerId to join room $roomNumber")
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

      case WebSocketFrame(wsFrame) => {
        // Once handshaking has taken place, we can now process frames sent from the client
        log.debug("chat from:" + wsFrame.webSocketId);

        wsFrame.endPoint.pathSegments match {
          case "websocket" :: roomNumber :: playerId :: Nil if roomNumber != "" && playerId != "" =>
            scrumGame ! Data(roomNumber, wsFrame.readText())
          case _ =>
            log.warn(s"invalid wsFrame endpoint: ${wsFrame.endPoint}")
            None
        }
      }

      case unknown => log.error(s"could not match ${unknown.getClass().getName()} = ${unknown}")
    })

    val webServer: WebServer = new WebServer(WebServerConfig(hostname = interface, port = staticPort), routes, actorSystem)
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run {
        webServer.stop()
        actorSystem.shutdown()
      }
    })

    actorSystem.actorOf(Props(classOf[ScrumGameActor], webServer.webSocketConnections), "scrumGame");

    webServer.start()

    System.out.println(s"Serving web content out of ${contentPath}")
    System.out.println(s"Open a few browsers and navigate to http://${interface}:${staticPort}. Start playing!")
  }
}

object HttpDataFactory {
  val value = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE)
}