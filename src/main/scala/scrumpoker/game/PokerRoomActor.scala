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

import akka.actor.{ ActorLogging, Actor }
import org.jboss.netty.channel.group.ChannelGroup
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.jboss.netty.channel.Channel
import argonaut.Argonaut._
import org.mashupbots.socko.webserver.WebSocketConnections
import scala.concurrent.duration._
import akka.actor.ReceiveTimeout
import akka.actor.PoisonPill

/**
 * maintains the state of a scrum poker room
 * @param socketConnections a container to hold the channels connected to this room
 */
class PokerRoomActor(roomNumber: String) extends Actor with ActorLogging {

  type PlayerId = Long
  type PlayerConnection = String

  private[this] var cardsDrawn = Map.empty[PlayerId, CardDrawn]
  private[this] var playerSessions = Map.empty[PlayerId, PlayerConnection]

  context.setReceiveTimeout(1 hour)

  def receive = {
    case Registration(roomNumber, playerId, connectionId) =>
      playerSessions += (playerId -> connectionId)
      sender ! Response(Seq(roomSize, drawnSize), playerSessions.values.toSet)
    case cd: CardDrawn =>
      cardsDrawn += (cd.player -> cd)
      sender ! Response(Seq(drawnSize), playerSessions.values.toSet)
    case cud: CardUndrawn =>
      cardsDrawn -= cud.player
      sender ! Response(Seq(drawnSize), playerSessions.values.toSet)
    case r: Reveal =>
      sender ! Response(Seq(cardSet), playerSessions.values.toSet)
    case r: Reset =>
      cardsDrawn = Map.empty[PlayerId, CardDrawn]
      sender ! Response(Seq(reset, roomSize, drawnSize), playerSessions.values.toSet)
    case exit: PlayerExit =>
      cardsDrawn -= exit.player
      playerSessions -= exit.player
      sender ! Response(Seq(roomSize), playerSessions.values.toSet)
    case Closed(connection) =>
      val inverted = (Map() ++ playerSessions.map(_.swap))
      inverted get connection match {
        case None =>
        case Some(player) =>
          cardsDrawn -= player
          playerSessions -= player
          sender ! Response(Seq(roomSize), playerSessions.values.toSet)
      }
    case ReceiveTimeout =>
      log.info(s"Shutting down roomNumber:${roomNumber} after recieve timeout")
      self ! PoisonPill
    case unknown =>
      log.warning(s"Ignoring unknown message $unknown")
  }

  def roomSize = {
    RoomSize(playerSessions.size).asJson.toString()
  }

  def drawnSize = {
    DrawnSize(cardsDrawn.size).asJson.toString()
  }

  def cardSet = {
    CardSet(cardsDrawn.values.toList.reverse).asJson.toString()
  }

  def reset = {
    Reset().asJson.toString()
  }
}

