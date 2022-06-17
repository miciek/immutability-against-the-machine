object Intro {
  /**
    * How does immutability work?
    * - String.concat, List.appendedAll, String.substring, List.appended, List.slice
    * - Map.get, Map.updated
    * - Option.orElse
    */

  {
    val ab = "ab"
    val cd = "cd"
    val abcd = ab.concat(cd)
    assert(abcd == "abcd")
  }

  {
    val ab = List("a", "b")
    val cd = List("c", "d")
    val abcd = ab.appendedAll(cd)
    assert(abcd == List("a", "b", "c", "d"))

    val abcde = abcd.appended("e")
    assert(abcde == List("a", "b", "c", "d", "e"))
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
    val apple = map.updated("Apple", 1)
    val fruits = apple.updated("Mango", 5)

    println(fruits)

    val result: Option[Int] = fruits.get("Banana").orElse(fruits.get("Mango")).orElse(fruits.get("Apple"))
    println(result)
    assert(result.contains(5))
  }
}
