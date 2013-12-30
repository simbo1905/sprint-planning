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

/**
 * maintains the state of a scrum poker room
 * @param socketConnections a container to hold the channels connected to this room
 */
class PokerRoomActor(private val webSocketConnections: WebSocketConnections) extends Actor with ActorLogging {

  type PlayerId = Long
  type PlayerConnection = String

  private[this] var cardsDrawn = Map.empty[PlayerId, CardDrawn]
  private[this] var playerSessions = Map.empty[PlayerId, PlayerConnection]

  def receive = {
    case Registration(roomNumber, playerId, connectionId) => {
      playerSessions += (playerId -> connectionId)
      write(roomSize)
      write(drawnSize)
    }
    case cd: CardDrawn => {
      cardsDrawn += (cd.player -> cd)
      write(drawnSize)
    }
    case cud: CardUndrawn => {
      cardsDrawn -= cud.player
      write(drawnSize)
    }
    case r: Reveal => {
      write(cardSet)
    }
    case exit: PlayerExit => {
      cardsDrawn -= exit.player
      write(roomSize)
    }
    case r: Reset => {
      cardsDrawn = Map.empty[PlayerId, CardDrawn]
      write(reset)
      write(roomSize)
      write(drawnSize)
    }
    case unknown => {
      log.warning(s"Ignoring unknown message $unknown")
    }
  }

  def write(msg: String) {
    log.info(msg)
    playerSessions.values foreach {
      webSocketConnections.writeText(msg, _)
    }
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

