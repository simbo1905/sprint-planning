package scrumpoker.game

import org.scalatest.matchers.MustMatchers
import org.scalatest.GivenWhenThen
import org.scalatest.FunSpec
import scala.Some
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MessageSpec extends FunSpec with GivenWhenThen with MustMatchers {

  import argonaut.Argonaut._
  import Message._

  describe("messages"){
    it("must be able to roundtrip a room size as json"){
      
      Given("a room size of 99")
      val roomSize = RoomSize(99)
      
      When("we turn it into json")
      val json = roomSize.asJson.toString()
      
      And("turn it back into an object")
      val umarshaledRoomsize = json.decodeOption[RoomSize]
      
      And("extract the room size values from the object")
      val roomSizeExtracted = umarshaledRoomsize match { 
         case Some(RoomSize(x,_)) => x
         case _ => Unit
      }
      
      Then("tah-dah!")
      roomSizeExtracted must be (99)
    }
    
    it("must be able to rountrip a card drawn as json"){

      Given("a card drawn")
      val cardDrawn = CardDrawn(99L,9)

      When("we turn it into json")
      val json = cardDrawn.asJson.toString();

      And("turn it back into an object")
      val unmarshaledCarddrawn = json.decodeOption[CardDrawn]

      And("extract the card drawn values")
      val cdValues = unmarshaledCarddrawn match {
        case Some(CardDrawn(x,y,_)) => (x,y)
        case _ => Unit
      }

      Then("tah-dah!")
      cdValues must be ((99L,9))
    }

    it("must be able to roundtrip a card undrawn as json") {

      Given("a card undrawn for player 99")
      val cardUndrawn = CardUndrawn(99)

      When("we turn it into json")
      val json = cardUndrawn.asJson.toString()

      And("turn it back into an object")
      val unmarshaledCardUndrawn = json.decodeOption[CardUndrawn]

      And("exract the card undrawn player value")
      val player = unmarshaledCardUndrawn match {
        case Some(CardUndrawn(x,_)) => x
        case _ => Unit
      }

      Then("tah-dah!")
      player must be (99)
    }

    it("must be able to roundtrip a drawn size as json"){

      Given("a drawn size of 99")
      val drawnSize = DrawnSize(99)

      When("we turn it into json")
      val json = drawnSize.asJson.toString()

      And("turn it back into an object")
      val unmarshaledDrawnSize = json.decodeOption[DrawnSize]

      And("extract the size from it")
      val size = unmarshaledDrawnSize match {
        case Some(DrawnSize(x,_)) => x
        case _ => Unit
      }

      Then("tah-dah!")
      size must be (99)
    }

    it("must be able to roundtrip a reveal as json"){

      Given("a reveal")
      val reveal = Reveal()

      When("we turn it into json")
      val json = reveal.asJson.toString()

      And("turn it back into an object")
      val unmarshaledReveal = json.decodeOption[Reveal]

      And("extract the reveal")
      val r = unmarshaledReveal match {
        case Some(Reveal(_)) => unmarshaledReveal.get
        case _ => Unit
      }

      Then("tah-dah!")
      r must equal (reveal)
    }

    it("must be able to roundtrip a card set as json"){

      Given("a card set of three card drawns")
      val cardSet = CardSet(List(CardDrawn(1,2),CardDrawn(3,4)))

      When("we turn it into json")
      val json = cardSet.asJson.toString()

      And("turn it back into an object")
      val unmarshaledCardset = json.decodeOption[CardSet]

      And("extract the cards drawn")
      val cardsDrawnList = unmarshaledCardset match {
        case Some(CardSet(list,_)) => list
        case _ => Nil
      }

      Then("tah-dah!")
      cardsDrawnList must equal ( List(CardDrawn(1,2),CardDrawn(3,4) ) )
    }

    it("must be able to roundtrip a player exit set as json"){

      Given("a player exit")
      val exit = PlayerExit(98765432100L)

      When("we turn it into json")
      val json = exit.asJson.toString()

      And("turn it back into an object")
      val unmarshaledExit = json.decodeOption[PlayerExit]

      And("extract the player exit")
      val player = unmarshaledExit match {
        case Some(PlayerExit(player,_)) => player
        case _ => Nil
      }

      Then("tah-dah!")
      player must equal ( 98765432100L )
    }

    it("must be able to roundtrip the superclass") {

      Given("a card set of three card drawns")
      val cardSet = CardSet(List(CardDrawn(1,2),CardDrawn(3,4)))

      When("we turn it into json")
      val json = cardSet.asJson.toString()

      And("turn it back into an message")
      val unmarshaledCardset = json.asMessage

      And("extract the cards drawn")
      val cardsDrawnList = unmarshaledCardset match {
        case Some(CardSet(list,_)) => list
        case _ => Nil
      }

      Then("tah-dah!")
      cardsDrawnList must equal ( List(CardDrawn(1,2),CardDrawn(3,4) ) )

    }
  }

}