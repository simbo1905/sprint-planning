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
class PokerRoomActor extends Actor with ActorLogging {

  type PlayerId = Long
  type PlayerConnection = String

  private[this] var cardsDrawn = Map.empty[PlayerId, CardDrawn]
  private[this] var playerSessions = Map.empty[PlayerId, PlayerConnection]

  def receive = {
    case Registration(roomNumber, playerId, connectionId) => {
      playerSessions += (playerId -> connectionId)
      sender ! roomSize
      sender ! drawnSize
    }
    case cd: CardDrawn => {
      cardsDrawn += (cd.player -> cd)
      sender ! drawnSize
    }
    case cud: CardUndrawn => {
      cardsDrawn -= cud.player
      sender ! drawnSize
    }
    case r: Reveal => {
      sender ! cardSet
    }
    case exit: PlayerExit => {
      cardsDrawn -= exit.player
      sender ! roomSize
    }
    case r: Reset => {
      cardsDrawn = Map.empty[PlayerId, CardDrawn]
      sender ! reset
      sender ! roomSize
      sender ! drawnSize
    }
    case unknown => {
      log.warning(s"Ignoring unknown message $unknown")
    }
  }

  def roomSize = {
    Response(RoomSize(playerSessions.size).asJson.toString(), playerSessions.values.toSet)
  }

  def drawnSize = {
    Response(DrawnSize(cardsDrawn.size).asJson.toString(), playerSessions.values.toSet)
  }

  def cardSet = {
    Response(CardSet(cardsDrawn.values.toList.reverse).asJson.toString(), playerSessions.values.toSet)
  }

  def reset = {
    Response(Reset().asJson.toString(), playerSessions.values.toSet)
  }
}

