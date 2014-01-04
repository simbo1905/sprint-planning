package scrumpoker.game

import akka.actor.{ ActorLogging, Actor }
import scala.concurrent.duration._
import akka.actor.ReceiveTimeout
import akka.actor.PoisonPill

case class PollRequest(player: String)
case class PollResponse(jsons: Seq[String])

/**
 * Legacy browsers require that we buffer messages on a per player basis for the browser to poll.
 */
class PollingPlayerActor(player: String) extends Actor with ActorLogging {

  context.setReceiveTimeout(1 minutes)

  private[this] var buffered = Seq.empty[String]

  def receive = {

    case json: String =>
      buffered = json +: buffered
      log.debug(s"buffered.size:${buffered.size}, json:${json}")

    case PollRequest(p) =>
      assert(p == player, "was polled for wrong player. my player is $player but was polled for $p")
      log.debug(s"poll response with size:${buffered.size}")
      sender ! PollResponse(buffered.reverse)
      buffered = Seq.empty[String]

    case ReceiveTimeout =>
      context.parent ! StopPlayer(player)

    case unknown =>
      log.warning(s"Ignoring unknown message $unknown")
  }
}