package scrumpoker

package object game {

  import scala.collection.IterableLike
  import scala.collection.generic.CanBuildFrom

  // http://stackoverflow.com/a/3912833/329496
  class RichCollection[A, Repr](xs: IterableLike[A, Repr]) {
    def distinctBy[B, That](f: A => B)(implicit cbf: CanBuildFrom[Repr, A, That]) = {
      val builder = cbf(xs.repr)
      val i = xs.iterator
      var set = Set[B]()
      while (i.hasNext) {
        val o = i.next
        val b = f(o)
        if (!set(b)) {
          set += b
          builder += o
        }
      }
      builder.result
    }
  }

  implicit def toRich[A, Repr](xs: IterableLike[A, Repr]) = new RichCollection(xs)
}