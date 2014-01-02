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

case class Initialize(wsc: WebSocketConnections)
case class Registration(roomNumber: String, playerId: Long, connectionId: String)
case class Data(roomNumber: String, json: String)

/**
 * TODO consider resource leaks and schedule to kill off rooms
 */
class ScrumGameActor extends Actor with ActorLogging {

  import Message._

  private[this] var rooms = Map.empty[String, ActorRef]

  def getOrCreateRoom(room: String): ActorRef = {
    if (rooms contains room) {
      return rooms(room)
    } else {
      log.info(s"new PokerRoomActor created for room ${room}")
      val newRoom = context.actorOf(Props(new PokerRoomActor(webSocketConnections.get)))
      rooms += (room -> newRoom)
      return newRoom;
    }
  }

  var webSocketConnections: Option[WebSocketConnections] = None

  def uninitialized: Receive = {
    case Initialize(wsc) =>
      log.info(s"becoming initialised with webSocketConnections:$webSocketConnections")
      webSocketConnections = Some(wsc)
      context become initialized
    case unknown => log.error(s"Received ${unknown.getClass.getName} whilst uninitialized: $unknown")
  }

  def initialized: Receive = {
    case Data("None", text) =>
      log.warning(s"Ignoring Data with roomnumber=None and text='${text}}'")
    case Data(roomNumber, json) =>
      getOrCreateRoom(roomNumber) ! json.asMessage.getOrElse(None)
    case r @ Registration(roomNumber, playerId, connectionId) =>
      getOrCreateRoom(roomNumber) ! r;
    case x =>
      log.warning(s"Ignorming unknown message: ${x}");
  }

  def receive = uninitialized
}