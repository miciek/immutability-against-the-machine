object Intro {

  /** How does immutability work?
    *
    * Teaching by example:
    * - String.concat, List.appendedAll, String.substring, List.appended, List.slice
    * - Map.get, Map.updated
    * - Option.orElse
    */

  {
    val ab   = "ab"
    val cd   = "cd"
    val abcd = ab.concat(cd)
    assert(abcd == "abcd")
  }

  {
    val ab   = List("a", "b")
    val cd   = List("c", "d")
    val abcd = ab.appendedAll(cd)
    assert(abcd == List("a", "b", "c", "d"))

    val abcde = abcd.appended("e")
    assert(abcde == List("a", "b", "c", "d", "e"))
  }

  {
    val abcd  = List("a", "b", "c", "d")
    val empty = List()

    assert(abcd.headOption == Some("a"))
    assert(empty.headOption == None)
  }

  {
    val abcd = "abcd"
    val bc   = abcd.substring(1, 3)
    assert(bc == "bc")

    println(bc)
  }

  {
    val abcd = List("a", "b", "c", "d")
    val bc   = abcd.slice(1, 3)
    assert(bc == List("b", "c"))

    println(bc)
  }

  {
    val map: Map[String, Int] = Map.empty
    val apple                 = map.updated("Apple", 1)
    val fruits                = apple.updated("Mango", 5)

    println(fruits)

    val result: Option[Int] = fruits.get("Banana").orElse(fruits.get("Mango")).orElse(fruits.get("Apple"))
    println(result)
    assert(result.contains(5))
  }

  {
    val numbers = List(1, 2, 3, 4, 5)
    val doubles = numbers.map(i => i * 2) // List(2, 4, 6, 8, 10)
    assert(doubles == List(2, 4, 6, 8, 10))
  }

  val tvShow                                       = "The Wire (2002-2008)"
  def extractYearStart(show: String): Option[Int]  = ???
  def extractYearEnd(show: String): Option[Int]    = ???
  def extractSingleYear(show: String): Option[Int] = ???

  def year: Option[Int] = extractYearStart(tvShow).orElse(extractSingleYear(tvShow))
}
