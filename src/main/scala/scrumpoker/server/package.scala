package scrumpoker

package object server {
  implicit class StringImprovements(val s: String) {
    import scala.util.control.Exception._
    def toLongOpt = catching(classOf[NumberFormatException]) opt s.toLong
  }
}