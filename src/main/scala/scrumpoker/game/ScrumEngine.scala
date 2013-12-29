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

import akka.actor.{ActorLogging, Actor, Props, ActorRef}
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.group.DefaultChannelGroup

case class Registration(channel: Channel, roomNumber: String, sessionId: String)
case class Data(roomNumber: String, json: String)

/**
 * TODO consider resource leaks and schedule to kill off rooms
 * http://stackoverflow.com/questions/8975009/netty-closing-websockets-correctly?rq=1
 */
class ScrumGameActor extends Actor with ActorLogging {

  import Message._
  
  private val rooms = collection.mutable.Map[String, ActorRef]() // TODO a var of an immutable is better
  
  def getOrCreateRoom(room: String): ActorRef = {
    if( rooms contains room ) { 
      return rooms(room);
    } else {
      log.info(s"new PokerRoomActor created for room ${room}")
      val newRoom = context.actorOf(Props(new PokerRoomActor(new DefaultChannelGroup())));
      rooms += (room -> newRoom);
      return newRoom;
    }
  }
  
  def receive = {
    case Data("None", text) =>
      log.warning(s"Ignoring Data with roomnumber=None and text='${text}}'")
    case Data(roomNumber,json) =>
      getOrCreateRoom(roomNumber) ! json.asMessage.getOrElse(None);
    case Registration(channel, roomNumber, _) =>
      getOrCreateRoom(roomNumber) ! channel;
    case x =>
      log.warning(s"Ignorming unknown message: ${x}");
  }
}