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
import scala.concurrent.Await
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
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

  implicit val timeout = Timeout(750 milliseconds)

  describe("a ScrumPokerRoom actor") {

    it("should send the size of the room membership and the cards drawn when a player joins") {

      Given("a pokerroom")
      val pokerRoom = TestActorRef(Props(new ScrumGameActor))

      When("the pokerroom is sent a player registration")
      val future = pokerRoom ? Registration("room1", 99, "connection99")

      And("when we extract the response")
      val result = Await.result(future, timeout.duration).asInstanceOf[Response]

      Then("we must be sent the room size and a zero card drawn count")
      result.json.map(_.asMessage).flatten must equal(Seq(RoomSize(1), DrawnSize(0)))
    }

    it("should send the total number of card that have been drawn") {

      Given("a pokerroom")
      val pokerRoom = TestActorRef(Props(new PokerRoomActor()))

      When("2 players join")
      pokerRoom ! Registration("room1", 11L, "connection11")
      pokerRoom ! Registration("room1", 21L, "connection21")

      And("notifed that 2 room members have selected cards")
      pokerRoom ! CardDrawn(11L, 1)
      pokerRoom ! CardDrawn(21L, 2)

      And("we capture the responses")
      val messages = receiveWhile(500 milliseconds, 500 milliseconds, 6) {
        case r: Response => r
      }

      Then("we must be sent the room size and a zero card drawn count")
      extract(messages) must equal(Seq(RoomSize(1), DrawnSize(0), RoomSize(2), DrawnSize(0), DrawnSize(1), DrawnSize(2)))
    }

    it("should respond to undrawn cards") {

      Given("a pokerroom")
      val pokerRoom = TestActorRef(Props(new PokerRoomActor()))

      When("3 players join")
      pokerRoom ! Registration("room1", 11L, "connection11")
      pokerRoom ! Registration("room1", 21L, "connection21")
      pokerRoom ! Registration("room1", 31L, "connection31")

      And("notifed that 3 room members have selected cards and one unselects")
      pokerRoom ! CardDrawn(11L, 1)
      pokerRoom ! CardDrawn(21L, 2)
      pokerRoom ! CardDrawn(31L, 3)
      pokerRoom ! CardUndrawn(21L)

      And("we capture the responses")
      val messages = receiveWhile(500 millisecond, 500 millisecond, 10) {
        case r: Response => r
      }

      Then("the responses are a card drawn sequence 0,1,2,3,2")
      extract(messages) must equal(Seq(RoomSize(1), DrawnSize(0), RoomSize(2), DrawnSize(0), RoomSize(3), DrawnSize(0), DrawnSize(1), DrawnSize(2), DrawnSize(3), DrawnSize(2)))
    }

    it("should reveal which cards have been drawn") {

      Given("a pokerroom")
      val pokerRoom = TestActorRef(Props(new PokerRoomActor()))

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

      And("we capture the responses")
      val messages = receiveWhile(500 millisecond, 500 millisecond, 10) {
        case r: Response => r
      }

      Then("the last response message contains cards 1, 3, 2")
      extract(messages).last must equal(CardSet(List(CardDrawn(21L, 5), CardDrawn(31L, 3), CardDrawn(11L, 1))))
    }

    it("should remove a players card on exit") {

      Given("a pokerroom")
      val pokerRoom = TestActorRef(Props(new PokerRoomActor()))

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

      And("we capture the responses")
      val messages = receiveWhile(500 millisecond, 500 millisecond, 10) {
        case r: Response => r
      }

      Then("the last response message only has the two remaining players cards")
      extract(messages).last must equal(CardSet(List(CardDrawn(31L, 3), CardDrawn(11L, 1))))
    }

    it("should respond to a reset correctly") {
      Given("a pokerroom")
      val pokerRoom = TestActorRef(Props(new PokerRoomActor()))

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

      And("we capture the responses")
      val messages = receiveWhile(500 millisecond, 500 millisecond, 10) {
        case r: Response => r
      }

      Then("the group is notified of the reset, room size and no cards drawn")
      val msg = extract(messages)
      msg(msg.length - 3) must equal(Reset())
      msg(msg.length - 2) must equal(RoomSize(3))
      msg(msg.length - 1) must equal(DrawnSize(0))
    }

  }

  def extract(rs: Seq[Response]): Seq[Message] = {
    rs.map(_.json.map(_.asMessage)).flatten.flatten
  }
}
