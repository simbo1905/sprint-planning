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
package scrumpoker.game

import akka.actor.{ ActorLogging, Actor, Props, ActorRef }
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.group.DefaultChannelGroup
import org.mashupbots.socko.webserver.WebSocketConnections
import akka.actor.PoisonPill
import org.mashupbots.socko.webserver.WebServer

case class Initialize(wsc: WebSocketConnections)
case class Registration(roomNumber: String, playerId: Long, connectionId: String)
case class Data(roomNumber: String, json: String)
case class Response(json: Seq[String], connections: Set[String])
case class Closed(connectionId: String)
case class Stop(room: String)

/**
 * Supervises a set of poker room actor children and adapts the websockets messages to the game message
 */
class ScrumGameActor(webServer: WebServer) extends Actor with ActorLogging {

  import Message._

  private[this] var rooms = Map.empty[String, ActorRef]

  def getOrCreateRoom(room: String): ActorRef = {
    if (rooms contains room) {
      return rooms(room)
    } else {
      log.info(s"New PokerRoomActor created for room ${room}")
      val newRoom = context.actorOf(Props(new PokerRoomActor(room)))
      rooms += (room -> newRoom)
      return newRoom;
    }
  }

  def receive: Receive = {
    case Data("None", text) =>
      log.warning(s"Ignoring Data with roomnumber=None and text='${text}}'")
    case Data(roomNumber, json) =>
      getOrCreateRoom(roomNumber) ! json.asMessage.getOrElse(None)
    case r @ Registration(roomNumber, playerId, connectionId) =>
      getOrCreateRoom(roomNumber) ! r
    case c @ Closed(_) =>
      rooms.values foreach {
        _ ! c
      }
    case Stop(room) =>
      rooms get room match {
        case None => log.warning(s"sent a stop for a room which we don't contain room:$room rooms.size=${rooms.size}")
        case Some(c) =>
          log.info(s"Stopping PokerRoomActor for room ${room}")
          rooms -= room
          c ! PoisonPill
      }
    case Response(json, connections) => json foreach { msg =>
      try {
        webServer.webSocketConnections.writeText(msg, connections)
      } catch {
        case e: Throwable => log.error(e, s"unable to write ${msg} to ${connections} ")
      }
    }
    case x =>
      log.warning(s"Ignorming unknown message: ${x}");
  }
}