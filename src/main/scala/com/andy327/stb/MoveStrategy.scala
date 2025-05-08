package com.andy327.stb

object MoveStrategy {
  val allTiles: Set[Int] = (1 to 9).toSet
  val combinations: Map[Int, List[Set[Int]]] = allTiles.subsets()
    .filter(_.nonEmpty).toList
    .groupBy(_.sum)
    .view.filterKeys(roll => roll >= 2 && roll <= 12)
    .toMap
}

trait MoveStrategy {
  def chooseMove(open: Set[Int], rollTotal: Int): Option[Set[Int]]
}

object RandomStrategy extends MoveStrategy {
  private val rand = new scala.util.Random

  def chooseMove(open: Set[Int], rollTotal: Int): Option[Set[Int]] = {
    val possibleCombos = MoveStrategy.combinations(rollTotal)
    val indices = rand.shuffle(possibleCombos.indices.toList)

    indices.iterator
      .map(possibleCombos)
      .find(_.subsetOf(open))
  }
}

object GreedyStrategy extends MoveStrategy {
  val greedyOrdering: Ordering[Set[Int]] = { // order by highest numbers closed and then fewest tiles closed
    val byMaxDesc = Ordering.by[Set[Int], Int](_.max).reverse
    val bySizeAsc = Ordering.by[Set[Int], Int](_.size)
    byMaxDesc.orElse(bySizeAsc)
  }

  val sortedCombos: Map[Int, List[Set[Int]]] = MoveStrategy.combinations.view.mapValues(_.sorted(greedyOrdering)).toMap

  def chooseMove(open: Set[Int], rollTotal: Int): Option[Set[Int]] = sortedCombos(rollTotal).find(_.subsetOf(open))
}

object MaxTilesStrategy extends MoveStrategy {
  val maxTilesOrdering: Ordering[Set[Int]] = { // order by highest number of tiles closed and then highest numbers closed
    val bySizeDesc = Ordering.by[Set[Int], Int](_.size).reverse
    val byMaxDesc = Ordering.by[Set[Int], Int](_.max).reverse
    bySizeDesc.orElse(byMaxDesc)
  }

  val sortedCombos: Map[Int, List[Set[Int]]] = MoveStrategy.combinations.view.mapValues(_.sorted(maxTilesOrdering)).toMap

  def chooseMove(open: Set[Int], rollTotal: Int): Option[Set[Int]] = sortedCombos(rollTotal).find(_.subsetOf(open))
}
