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
import org.mashupbots.socko.webserver.WebSocketConnections
import akka.actor.PoisonPill
import org.mashupbots.socko.webserver.WebServer

sealed trait Connection {
  def id: String
}
case class Websocket(id: String) extends Connection
case class Polling(id: String) extends Connection
case class Registration(roomNumber: String, playerId: Long, connection: Connection)
case class Data(roomNumber: String, json: String)
case class Response(jsons: Seq[String], connections: Set[Connection])
case class Closed(connection: Connection)
case class StopRoom(room: String)
case class StopPlayer(player: String)
case object Close

/**
 * Supervises a set of poker room actor children and adapts the websockets messages to the game message
 */
class ScrumGameActor(webSocketConnections: WebSocketConnections) extends Actor with ActorLogging {

  import Message._

  private[this] var rooms = Map.empty[String, ActorRef]
  private[this] var polling = Map.empty[String, ActorRef]

  def getOrCreateRoom(room: String): ActorRef = {
    if (rooms contains room) {
      return rooms(room)
    } else {
      log.info(s"PokerRoomActor created for room ${room}")
      val newRoom = context.actorOf(Props(classOf[PokerRoomActor], room))
      rooms += (room -> newRoom)
      return newRoom;
    }
  }

  def receive: Receive = {
    case Data("None", text) => // TODO use an option to collapse this block into the next
      log.warning(s"Ignoring Data with roomnumber=None and text='${text}}'")

    case Data(roomNumber, json) =>
      getOrCreateRoom(roomNumber) ! json.asMessage.getOrElse(None)

    case r @ Registration(roomNumber, playerId, connection) =>
      getOrCreateRoom(roomNumber) ! r
      connection match {
        case Websocket(_) => // nop
        case Polling(player) =>
          if (polling contains player) {
            log.warning(s"re-registration of player $playerId entering room $roomNumber")
          } else {
            log.info(s"registring polling player actor for $player")
            val newPlayer = context.actorOf(Props(classOf[PollingPlayerActor], player), player)
            polling += (player -> newPlayer)
          }
      }

    case c @ Closed(_) =>
      log.info(s"connection closed $c")
      rooms.values foreach {
        _ ! c
      }

    case StopRoom(room) =>
      rooms get room match {
        case None => log.warning(s"sent a stop for a room which we don't contain room:$room, rooms.size=${rooms.size}")
        case Some(ref) =>
          log.info(s"Stopping PokerRoomActor for room ${room}")
          rooms -= room
          ref ! PoisonPill
      }

    case StopPlayer(player) =>
      polling get player match {
        case None => log.warning(s"sent a stop for a palyer which we don't contain player:$player, polling.size=${polling.size}")
        case Some(ref) =>
          log.info(s"Stopping PollingPlayerActor for player ${player}")
          polling -= player
          ref ! PoisonPill
          self ! Closed(Polling(player))
      }

    case Response(json, connections) => json foreach { msg =>
      val (websocketids, pollingids) = connections.partition(_.isInstanceOf[Websocket]) // TODO push this work down to the room actor
      try {
        webSocketConnections.writeText(msg, websocketids.map(_.id))
      } catch {
        case e: Throwable => log.error(e, s"unable to write ${msg} to ${connections} ")
      }
      pollingids.map(_.id) foreach {
        player =>
          polling get player match {
            case None => log.warning(s"got a response with a polling player which we don't contain player:$player, polling.size:${polling.size}")
            case Some(ref) => ref ! msg
          }
      }
    }

    case p @ PollRequest(player) =>
      polling get player match {
        case None =>
          log.warning(s"got a poll for $player but that is not in the polling map of size ${polling.size} replying with close")
          sender ! Close
        case Some(ref) => ref forward p
      }

    case x =>
      log.warning(s"Ignoring unknown message: ${x}");
  }
}