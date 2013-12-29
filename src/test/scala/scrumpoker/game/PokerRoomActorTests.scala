package scrumpoker.game

import scala.collection.JavaConverters.asScalaBufferConverter
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.group.ChannelGroup
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame
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

@RunWith(classOf[JUnitRunner])
class PokerRoomActorTests extends TestKit(ActorSystem("testSystem"))
  with ImplicitSender
  with FunSpec
  with GivenWhenThen
  with MustMatchers
  with MockitoSugar {

  import Message._

  describe("a ScrumPokerRoom actor") {

    it("should collect room member channels in a channel group") {

      Given("a pokerroom constructed with a channel group")
      val mockChannelGroup = mock[ChannelGroup]
      val pokerRoom = TestActorRef(Props(new PokerRoomActor(mockChannelGroup)))

      And("a channel to write to")
      val channel = mock[Channel]

      When("the pokerroom is sent the channel")
      pokerRoom ! channel

      Then("the channel has been added to the channel group")
      verify(mockChannelGroup).add(channel)
    }

    it("should send the size of the room membership and the cards drawn when a channel joins its channel group") {

      Given("a pokerroom constructed with a chanel group of size 99")
      val mockChannelGroup = mockChannelGroupOfSize(99)
      val pokerRoom = TestActorRef(Props(new PokerRoomActor(mockChannelGroup)))

      And("a channel to write to")
      val channel = mock[Channel]

      When("the pokerroom is sent a client connection channel")
      pokerRoom ! channel

      Then("when when we extradt the messages sent to the channel we were sent the room size and the card drawn count")
      val messages: Seq[Message] = capturedMessagesSentToChannelGroup(2, mockChannelGroup).flatten
      messages must equal(List(RoomSize(99), DrawnSize(0)))
    }

    it("should send the total number of card that have been drawn") {

      Given("a pokerroom constructed with a channel group of size 1")
      val mockChannelGroup = mockChannelGroupOfSize(1)
      val pokerRoom = TestActorRef(Props(new PokerRoomActor(mockChannelGroup)))

      When("notifed that 2 room members have selected a cards")
      pokerRoom ! CardDrawn(11L, 1)
      pokerRoom ! CardDrawn(21L, 2)

      And("we extract the carddrawn objects sent to the channel group")
      val msgs = capturedCardDrawnSentToChannelGroup(2, mockChannelGroup).flatten

      Then("the channel group been notified that one then two cards were drawn")
      msgs must equal(List(DrawnSize(1), DrawnSize(2)))
    }

    it("should send which cards have been undrawn") {

      Given("a pokerroom constructed with a channel group of size 1")
      val mockChannelGroup = mockChannelGroupOfSize(1)
      val pokerRoom = TestActorRef(Props(new PokerRoomActor(mockChannelGroup)))

      When("notifed that 2 room members have selected a cards")
      pokerRoom ! CardDrawn(11L, 1)
      pokerRoom ! CardDrawn(21L, 2)
      pokerRoom ! CardDrawn(31L, 3)
      pokerRoom ! CardUndrawn(21L)

      And("we extract the carddrawn objects sent to the channel group")
      val msgs = capturedCardDrawnSentToChannelGroup(4, mockChannelGroup).flatten

      Then("the channel group has seen a card drawn messages 1,2,3,2")
      msgs must equal(List(DrawnSize(1), DrawnSize(2), DrawnSize(3), DrawnSize(2)))
    }

    it("should reveal which cards have been drawn") {

      Given("a pokerroom constructed with a channel group of size 1")
      val mockChannelGroup = mockChannelGroupOfSize(1)
      val pokerRoom = TestActorRef(Props(new PokerRoomActor(mockChannelGroup)))

      When("notifed about cards drawn and undrawn")
      pokerRoom ! CardDrawn(11L, 1)
      pokerRoom ! CardDrawn(21L, 2)
      pokerRoom ! CardDrawn(31L, 3)
      pokerRoom ! CardUndrawn(21L)
      pokerRoom ! CardDrawn(21L, 5)

      And("told to reveal")
      pokerRoom ! Reveal()

      And("we extract all the cardset objects")
      val msg = capturedCardSetSentToChannelGroup(6, mockChannelGroup).flatten

      Then("the channel group has been sent a card set message containing cards 1, 3, 2")
      msg must equal(List(CardSet(List(CardDrawn(21L, 5), CardDrawn(31L, 3), CardDrawn(11L, 1)))))
    }

    it("should remove a players card on exit") {

      Given("a pokerroom constructed with a channel group of size 1")
      val mockChannelGroup = mockChannelGroupOfSize(1)
      val pokerRoom = TestActorRef(Props(new PokerRoomActor(mockChannelGroup)))

      When("notifed that 3 room members have selected a cards")
      pokerRoom ! CardDrawn(11L, 1)
      pokerRoom ! CardDrawn(21L, 2)
      pokerRoom ! CardDrawn(31L, 3)

      And("the middle player exits the game")
      pokerRoom ! PlayerExit(21L)

      And("we reveal the cards")
      pokerRoom ! Reveal();

      And("we extract the cardset sent to the channel group")
      val cs = capturedMessagesSentToChannelGroup(5, mockChannelGroup).flatten.map({
        case cs: CardSet => Some(cs)
        case _ => None
      }).flatten.head

      Then("the channel group been notified only of the other two remaining players cards")
      cs must equal(CardSet(List(CardDrawn(31L, 3), CardDrawn(11L, 1))))
    }

    it("should respond to a reset correctly") {
      Given("a pokerroom constructed with a channel group of size 1")
      val mockChannelGroup = mockChannelGroupOfSize(3)
      val pokerRoom = TestActorRef(Props(new PokerRoomActor(mockChannelGroup)))

      When("notifed that 3 room members have selected a cards")
      pokerRoom ! CardDrawn(11L, 1)
      pokerRoom ! CardDrawn(21L, 2)
      pokerRoom ! CardDrawn(31L, 3)

      And("we reset")
      pokerRoom ! Reset();

      And("we extract the messages sent to the channel group (3 members plus 3 reset messages)")
      val cs: Seq[Option[Message]] = capturedMessagesSentToChannelGroup(6, mockChannelGroup)

      Then("the channel group been notified only of the other two remaining players cards")
      cs(3) must equal(Some(Reset()))
      cs(4) must equal(Some(RoomSize(3)))
      cs(5) must equal(Some(DrawnSize(0)))
    }
  }

  def mockChannelGroupOfSize(size: Int): ChannelGroup = {
    val cg = mock[ChannelGroup]
    Mockito.when(cg.size()).thenReturn(size)
    cg
  }

  def capturedTextSentToChannelGroup(cg: ChannelGroup): String = {
    val captor = ArgumentCaptor.forClass(classOf[TextWebSocketFrame])
    verify(cg).write(captor.capture())
    val text = captor.getValue().getText();
    text
  }

  def capturedCardDrawnSentToChannelGroup(t: Int, cg: ChannelGroup) = {
    val captor = ArgumentCaptor.forClass(classOf[TextWebSocketFrame])
    verify(cg, times(t)).write(captor.capture())
    val texts = captor.getAllValues().asScala map { v => v.getText() }
    texts map { t => t.asMessage } map { o => o }
  }

  def capturedCardSetSentToChannelGroup(t: Int, cg: ChannelGroup) = {
    val captor = ArgumentCaptor.forClass(classOf[TextWebSocketFrame])
    verify(cg, times(t)).write(captor.capture())
    val texts = captor.getAllValues().asScala map { v => v.getText() } filter (_.contains("CardSet"))
    texts map { t => t.asMessage } map { o => o }
  }

  def capturedMessagesSentToChannelGroup(count: Int, cg: ChannelGroup) = {
    val captor = ArgumentCaptor.forClass(classOf[TextWebSocketFrame])
    verify(cg, times(count)).write(captor.capture())
    val texts = captor.getAllValues().asScala map { v => v.getText() }
    texts map { t => t.asMessage } map { o => o }
  }

}
