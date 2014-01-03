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
 */
class PokerRoomActor(roomNumber: String) extends Actor with ActorLogging {

  type PlayerId = Long
  type PlayerConnection = String

  private[this] var cardsDrawn = Map.empty[PlayerId, CardDrawn]
  private[this] var playerSessions = Map.empty[PlayerId, PlayerConnection]

  def connections = {
    playerSessions.values.toSet
  }

  context.setReceiveTimeout(20 minutes)

  def receive = {
    case Registration(roomNumber, playerId, connectionId) =>
      playerSessions += (playerId -> connectionId)
      sender ! Response(Seq(roomSize, drawnSize), connections)
    case cd: CardDrawn =>
      cardsDrawn += (cd.player -> cd)
      sender ! Response(Seq(drawnSize), connections)
    case cud: CardUndrawn =>
      cardsDrawn -= cud.player
      sender ! Response(Seq(drawnSize), connections)
    case r: Reveal =>
      sender ! Response(Seq(cardSet), connections)
    case r: Reset =>
      cardsDrawn = Map.empty[PlayerId, CardDrawn]
      sender ! Response(Seq(reset, roomSize, drawnSize), connections)
    case exit: PlayerExit =>
      cardsDrawn -= exit.player
      playerSessions -= exit.player
      sender ! Response(Seq(roomSize), connections)
    case Closed(connection) =>
      val inverted = (Map() ++ playerSessions.map(_.swap))
      inverted get connection match {
        case None =>
        case Some(player) =>
          cardsDrawn -= player
          playerSessions -= player
          sender ! Response(Seq(roomSize), connections)
      }
    case ReceiveTimeout =>
      context.parent ! Stop(roomNumber)
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

