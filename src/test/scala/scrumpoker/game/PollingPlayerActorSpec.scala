package scrumpoker.game

import scala.util.Random
import org.scalatest.BeforeAndAfterAll
import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import com.typesafe.config.ConfigFactory
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.DefaultTimeout
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import scala.concurrent.duration._
import scala.collection.immutable
import org.scalatest.matchers.MustMatchers
import org.scalatest.mock.MockitoSugar
import org.scalatest.GivenWhenThen
import org.junit.runner.RunWith
import org.scalatest.WordSpecLike
import akka.testkit.TestActorRef
import org.scalatest.junit.JUnitRunner
import akka.actor.ReceiveTimeout

@RunWith(classOf[JUnitRunner])
class PollingPlayerActorSpec extends TestKit(ActorSystem("PollingPlayerActorSpec"))
  with DefaultTimeout with ImplicitSender
  with WordSpecLike with MustMatchers with GivenWhenThen with BeforeAndAfterAll {

  import Message._

  override def afterAll {
    system.shutdown
  }

  val player = "player101"

  "polling player actor" should {

    "initially be empty" in {
      //Given("a polling player actor")
      val pollingPlayer = TestActorRef(Props(classOf[PollingPlayerActor], player))

      //When("it is polled")
      pollingPlayer ! PollRequest("player101")

      //Then("it responds with an empty response")
      expectMsg(PollResponse(List()))
    }

    "respond to a receive timeout by sending stop to its parent " in {

      //Given("a polling player actor")
      val pollingPlayer = actorWithParent(Props(classOf[PollingPlayerActor], player))

      //When("it is sees a timeout")
      pollingPlayer ! ReceiveTimeout

      //Then("it responds with an empty response")
      expectMsg(StopPlayer(player))

    }

    "buffer messages and return them in order when polled" in {
      //Given("a polling player actor")
      val pollingPlayer = TestActorRef(Props(classOf[PollingPlayerActor], player))

      //When("it is sent to messages then polled")
      pollingPlayer ! "hello"
      pollingPlayer ! "world"
      pollingPlayer ! PollRequest(player)

      //Then("it responds with the enqueued messages")
      expectMsg(PollResponse(Seq("hello", "world")))
    }

    "clear its buffer when polled" in {
      //Given("a polling player actor")
      val pollingPlayer = TestActorRef(Props(classOf[PollingPlayerActor], player))

      //When("it is sent to messages then polled")
      pollingPlayer ! "hello"
      pollingPlayer ! PollRequest(player)
      pollingPlayer ! PollRequest(player)

      //Then("it responds with the enqueued messages")
      expectMsg(PollResponse(Seq("hello")))
      expectMsg(PollResponse(Seq()))
    }

    /**
     * https://groups.google.com/forum/#!topic/akka-user/vEBcQSSN43k
     */
    def actorWithParent(props: Props) = {
      system.actorOf(Props(new Actor {
        val child = context.actorOf(Props(classOf[PollingPlayerActor], player))
        def receive = {
          case x => if (sender == child) testActor forward x else child forward x
        }
      }))

    }

  }

}
