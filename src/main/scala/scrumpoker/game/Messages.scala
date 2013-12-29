package scrumpoker.game

import argonaut._, Argonaut._

/**
 * Immutable messages representing activity in the game
 */
abstract class Message {
  /**
   * message type
   */
  def mType: String
}
case class RoomSize(size: Int, mType: String = "RoomSize") extends Message
case class CardDrawn(player: Long, card: Int, mType: String = "CardDrawn") extends Message
case class CardUndrawn(player: Long, mType: String = "CardUndrawn") extends Message
case class DrawnSize(size: Int, mType: String = "DrawnSize") extends Message
case class Reveal(mType: String = "Reveal") extends Message
case class CardSet(cards: List[CardDrawn], mType: String = "CardSet") extends Message
case class PlayerExit(player: Long, mType: String = "PlayerExit") extends Message
case class Reset(mType: String = "Reset") extends Message

object Message {

  /**
   * Arganaut implicits to provide conversions to and from JSON
   */
  implicit lazy val CodecRoomSize: CodecJson[RoomSize] = casecodec2(RoomSize.apply, RoomSize.unapply)("size", "mType")
  implicit lazy val CodecCardDrawn: CodecJson[CardDrawn] = casecodec3(CardDrawn.apply, CardDrawn.unapply)("player", "card", "mType")
  implicit lazy val CodecCardSet: CodecJson[CardSet] = casecodec2(CardSet.apply, CardSet.unapply)("cards", "mType")
  implicit lazy val CodecCardUndrawn: CodecJson[CardUndrawn] = casecodec2(CardUndrawn.apply, CardUndrawn.unapply)("player", "mType")
  implicit lazy val CodecDrawnSize: CodecJson[DrawnSize] = casecodec2(DrawnSize.apply, DrawnSize.unapply)("size", "mType")
  implicit lazy val CodecReveal: CodecJson[Reveal] = casecodec1(Reveal.apply, Reveal.unapply)("mType")
  implicit lazy val CodecPlayerExit: CodecJson[PlayerExit] = casecodec2(PlayerExit.apply, PlayerExit.unapply)("player", "mType")
  implicit lazy val CodecReset: CodecJson[Reset] = casecodec1(Reset.apply, Reset.unapply)("mType")

  def objectMessage(json: String): Option[Message] = {
    json match {
      case json if json contains "CardSet" => json.decodeOption[CardSet]
      case json if json contains "CardDrawn" => json.decodeOption[CardDrawn]
      case json if json contains "RoomSize" => json.decodeOption[RoomSize]
      case json if json contains "CardUndrawn" => json.decodeOption[CardUndrawn]
      case json if json contains "DrawnSize" => json.decodeOption[DrawnSize]
      case json if json contains "Reveal" => json.decodeOption[Reveal]
      case json if json contains "PlayerExit" => json.decodeOption[PlayerExit]
      case json if json contains "Reset" => json.decodeOption[Reset]
    }
  }

  class JasonMessageString(string: String) {
    def asMessage = objectMessage(string)
  }

  implicit def asMessage(string: String) = new JasonMessageString(string)

}