package scrumpoker.game

import scala.collection.JavaConverters.asScalaBufferConverter
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import org.scalatest.FunSpec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.MustMatchers
import org.scalatest.mock.MockitoSugar
import akka.actor.{ Props, ActorSystem }
import akka.testkit.{ TestActorRef, ImplicitSender, TestKit }
import scala.Some
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.mashupbots.socko.webserver.WebSocketConnections

@RunWith(classOf[JUnitRunner])
class PokerRoomActorTests extends TestKit(ActorSystem("testSystem"))
  with ImplicitSender
  with FunSpec
  with GivenWhenThen
  with MustMatchers
  with MockitoSugar {

  import Message._

  describe("a ScrumPokerRoom actor") {

    it("should send the size of the room membership and the cards drawn when a player joins") {

      Given("a pokerroom constructed with a mock websocketconnections")
      val mockWebSocketConnections = mock[WebSocketConnections]
      val pokerRoom = TestActorRef(Props(new PokerRoomActor(mockWebSocketConnections)))

      When("the pokerroom is sent a player registration")
      pokerRoom ! Registration("room1", 99, "connection99")

      And(" when we extract the messages sent to the websockets")
      val messages: Seq[(String, Message)] = capturedMessagesSentToConnections(2, mockWebSocketConnections)

      Then("we must be sent the room size and a zero card drawn count")
      messages must equal(Seq(
        ("connection99", RoomSize(1)), ("connection99", DrawnSize(0))))
    }

    it("should send the total number of card that have been drawn") {

      Given("a pokerroom constructed with a mock websocketconnections")
      val mockWebSocketConnections = mock[WebSocketConnections]
      val pokerRoom = TestActorRef(Props(new PokerRoomActor(mockWebSocketConnections)))

      When("2 players join")
      pokerRoom ! Registration("room1", 11L, "connection11")
      pokerRoom ! Registration("room1", 21L, "connection21")

      And("notifed that 2 room members have selected cards")
      pokerRoom ! CardDrawn(11L, 1)
      pokerRoom ! CardDrawn(21L, 2)

      And("we extract the messages sent to the websockets")
      val messages: Seq[Message] = captureDistinctMessagesSent(10, mockWebSocketConnections)

      Then("we must be sent the room size and a zero card drawn count")
      messages must equal(Seq(RoomSize(1), DrawnSize(0), RoomSize(2), DrawnSize(1), DrawnSize(2)))
    }

    it("should respond to undrawn cards") {

      Given("a pokerroom constructed with a mock websocketconnections")
      val mockWebSocketConnections = mock[WebSocketConnections]
      val pokerRoom = TestActorRef(Props(new PokerRoomActor(mockWebSocketConnections)))

      When("3 players join")
      pokerRoom ! Registration("room1", 11L, "connection11")
      pokerRoom ! Registration("room1", 21L, "connection21")
      pokerRoom ! Registration("room1", 31L, "connection31")

      And("notifed that 3 room members have selected cards and one unselects")
      pokerRoom ! CardDrawn(11L, 1)
      pokerRoom ! CardDrawn(21L, 2)
      pokerRoom ! CardDrawn(31L, 3)
      pokerRoom ! CardUndrawn(21L)

      And("we extract the messages sent to the players")
      val messages: Seq[(Int, Message)] = captureRunLengthMessagesSent(24, mockWebSocketConnections)

      Then("the channel group has seen a card drawn sequence 0,1,2,3,2")
      messages must equal(Seq((1, RoomSize(1)), (1, DrawnSize(0)), (2, RoomSize(2)), (2, DrawnSize(0)), (3, RoomSize(3)), (3, DrawnSize(0)), (3, DrawnSize(1)), (3, DrawnSize(2)), (3, DrawnSize(3)), (3, DrawnSize(2))))
    }

    it("should reveal which cards have been drawn") {

      Given("a pokerroom constructed with a mock websocketconnections")
      val mockWebSocketConnections = mock[WebSocketConnections]
      val pokerRoom = TestActorRef(Props(new PokerRoomActor(mockWebSocketConnections)))

      When("2 players join")
      pokerRoom ! Registration("room1", 11L, "connection11")
      pokerRoom ! Registration("room1", 21L, "connection21")
      pokerRoom ! Registration("room1", 31L, "connection31")

      And("notifed about cards drawn and undrawn")
      pokerRoom ! CardDrawn(11L, 1)
      pokerRoom ! CardDrawn(21L, 2)
      pokerRoom ! CardDrawn(31L, 3)
      pokerRoom ! CardUndrawn(21L)
      pokerRoom ! CardDrawn(21L, 5)

      And("told to reveal")
      pokerRoom ! Reveal()

      And("we extract the distinct messages sent")
      val msg = captureDistinctMessagesSent(30, mockWebSocketConnections)

      Then("the group has been sent a card set message containing cards 1, 3, 2")
      msg.last must equal(CardSet(List(CardDrawn(21L, 5), CardDrawn(31L, 3), CardDrawn(11L, 1))))
    }

    it("should remove a players card on exit") {

      Given("a pokerroom constructed with a mock websocketconnections")
      val mockWebSocketConnections = mock[WebSocketConnections]
      val pokerRoom = TestActorRef(Props(new PokerRoomActor(mockWebSocketConnections)))

      When("3 players join")
      pokerRoom ! Registration("room1", 11L, "connection11")
      pokerRoom ! Registration("room1", 21L, "connection21")
      pokerRoom ! Registration("room1", 31L, "connection31")

      And("notifed that 3 room members have selected a cards")
      pokerRoom ! CardDrawn(11L, 1)
      pokerRoom ! CardDrawn(21L, 2)
      pokerRoom ! CardDrawn(31L, 3)

      And("the middle player exits the game")
      pokerRoom ! PlayerExit(21L)

      And("we reveal the cards")
      pokerRoom ! Reveal();

      And("we extract the distinct messages sent")
      val msg = captureDistinctMessagesSent(27, mockWebSocketConnections)

      Then("the group been notified only of the other two remaining players cards")
      msg.last must equal(CardSet(List(CardDrawn(31L, 3), CardDrawn(11L, 1))))
    }

    it("should respond to a reset correctly") {
      Given("a pokerroom constructed with a mock websocketconnections")
      val mockWebSocketConnections = mock[WebSocketConnections]
      val pokerRoom = TestActorRef(Props(new PokerRoomActor(mockWebSocketConnections)))

      When("3 players join")
      pokerRoom ! Registration("room1", 11L, "connection11")
      pokerRoom ! Registration("room1", 21L, "connection21")
      pokerRoom ! Registration("room1", 31L, "connection31")

      And("notifed that 3 room members have selected cards")
      pokerRoom ! CardDrawn(11L, 1)
      pokerRoom ! CardDrawn(21L, 2)
      pokerRoom ! CardDrawn(31L, 3)

      And("we reset")
      pokerRoom ! Reset();

      And("we extract the messages sent to the players")
      val messages: Seq[(Int, Message)] = captureRunLengthMessagesSent(30, mockWebSocketConnections)

      Then("the group is notified of the reset, room size and no cards drawn")
      messages(messages.length - 3) must equal((3, Reset()))
      messages(messages.length - 2) must equal((3, RoomSize(3)))
      messages(messages.length - 1) must equal((3, DrawnSize(0)))
    }

  }

  def captureDistinctMessagesSent(count: Int, wsc: WebSocketConnections) = {
    val captorText = ArgumentCaptor.forClass(classOf[String])
    val captorConnection = ArgumentCaptor.forClass(classOf[String])
    verify(wsc, times(count)).writeText(captorText.capture(), captorConnection.capture())
    val messages = captorText.getAllValues().asScala.map(_.asMessage).flatten
    val uniqueOrdered = messages.distinctBy(_.toString)
    uniqueOrdered
  }

  def captureRunLengthMessagesSent(count: Int, wsc: WebSocketConnections) = {
    val captorText = ArgumentCaptor.forClass(classOf[String])
    val captorConnection = ArgumentCaptor.forClass(classOf[String])
    verify(wsc, times(count)).writeText(captorText.capture(), captorConnection.capture())
    val messages = captorText.getAllValues().asScala.map(_.asMessage).flatten
    var current = messages(0)
    var run = 0
    var runs = Seq.empty[(Int, Message)]
    messages foreach { m =>
      if (m == current) {
        run = run + 1
      } else {
        runs = (run, current) +: runs
        current = m
        run = 1
      }
    }
    runs = (run, current) +: runs
    var r = runs.reverse
    r
  }

  def capturedMessagesSentToConnections(count: Int, wsc: WebSocketConnections) = {
    val captorText = ArgumentCaptor.forClass(classOf[String])
    val captorConnection = ArgumentCaptor.forClass(classOf[String])
    verify(wsc, times(count)).writeText(captorText.capture(), captorConnection.capture())
    val messages = captorText.getAllValues().asScala.map(_.asMessage).flatten
    val connections = captorConnection.getAllValues().asScala
    connections.zip(messages)
  }

}
