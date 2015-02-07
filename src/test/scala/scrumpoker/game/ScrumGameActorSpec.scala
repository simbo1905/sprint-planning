package scrumpoker.game

import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import org.scalatest.matchers.MustMatchers
import org.scalatest.GivenWhenThen
import org.scalatest.BeforeAndAfterAll
import akka.testkit.DefaultTimeout
import org.junit.runner.RunWith
import org.scalatest.WordSpecLike
import akka.actor.ActorSystem
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.mashupbots.socko.webserver.WebServer
import akka.testkit.TestActorRef
import akka.actor.Props
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import org.mashupbots.socko.webserver.WebSocketConnections
import scala.collection.JavaConverters.asScalaBufferConverter

@RunWith(classOf[JUnitRunner])
class ScrumGameActorSpec extends TestKit(ActorSystem("ScrumGameActorSpec"))
  with DefaultTimeout with ImplicitSender
  with WordSpecLike with MustMatchers with GivenWhenThen with BeforeAndAfterAll with MockitoSugar {

  override def afterAll {
    system.shutdown
  }

  "scrum game actor" should {
    "should send json response messages to the webserver websockets" in {
     // Given("a scrum game actor with a mock webserver")
      val wsc = mock[WebSocketConnections]
      val scrumGame = TestActorRef(Props(new ScrumGameActor(wsc)))

      //When("it is sent some json for some websockets")
      scrumGame ! Response(Seq("hello", "world"), Set(Websocket("ws1"), Websocket("ws2")))

     // Then("both messages are sent out to all websockets")
      captureTextSentToConnections(wsc, 2) must equal(Seq((Set("ws1", "ws2"), "hello"), (Set("ws1", "ws2"), "world")))
    }
  }

  def captureTextSentToConnections(wsc: WebSocketConnections, count: Int) = {
    val captorText = ArgumentCaptor.forClass(classOf[String])
    val captorConnection = ArgumentCaptor.forClass(classOf[Iterable[String]])
    verify(wsc, times(count)).writeText(captorText.capture(), captorConnection.capture())
    val messages = captorText.getAllValues().asScala
    val connections = captorConnection.getAllValues().asScala
    connections.zip(messages)
  }

}