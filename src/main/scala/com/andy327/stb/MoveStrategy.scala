package com.andy327.stb

object MoveStrategy {
  val allTiles: State = (1 to 9).toSet
  val combinations: Map[Roll, List[Move]] = allTiles.subsets()
    .filter(_.nonEmpty).toList
    .groupBy(_.sum)
    .view.filterKeys(roll => roll >= 2 && roll <= 12)
    .toMap

  val allStates: List[State] = allTiles.subsets().toList

  val rolls: Seq[(Int, Int)] = for {
    r1 <- 1 to 6
    r2 <- 1 to 6
  } yield (r1, r2)

  /** Map of roll totals to the number of dice rolls that sum to that value (e.g., 2 -> 1, 7 -> 6, 10 -> 3, etc.) */
  val rollWeights: Map[Roll, Int] = rolls
    .map { case (r1, r2) => r1 + r2 }
    .groupBy(identity)
    .view.mapValues(_.size)
    .toMap

  /** Pre-computed map of sets of open tiles and the number of dice rolls that can be played in the next move */
  val numValidRolls: Map[State, Int] = allStates
    .map(combo => combo -> successfulNextRolls(combo))
    .toMap

  /** Returns the number of possible dice rolls that will result in a total that can be played with the remaining tiles */
  def successfulNextRolls(open: State): Int = {
    rollWeights
      .filter { case (total, _) => // keep possible roll totals that can be played after the current move
        combinations(total).exists(_.subsetOf(open))
      }
      .values
      .sum
  }
}

trait MoveStrategy {
  def chooseMove(open: State, rollTotal: Roll): Option[Move]
}

object RandomStrategy extends MoveStrategy {
  private val rand = new scala.util.Random

  def chooseMove(open: State, rollTotal: Roll): Option[Move] = {
    val possibleCombos = MoveStrategy.combinations(rollTotal)
    val indices = rand.shuffle(possibleCombos.indices.toList)

    indices.iterator
      .map(possibleCombos)
      .find(_.subsetOf(open))
  }
}

object GreedyStrategy extends MoveStrategy {
  val greedyOrdering: Ordering[Move] = { // order by highest numbers closed and then fewest tiles closed
    val byMaxDesc = Ordering.by[Move, Int](_.max).reverse
    val bySizeAsc = Ordering.by[Move, Int](_.size)
    byMaxDesc.orElse(bySizeAsc)
  }

  val sortedCombos: Map[Roll, List[Move]] = MoveStrategy.combinations.view.mapValues(_.sorted(greedyOrdering)).toMap

  def chooseMove(open: State, rollTotal: Roll): Option[Move] = sortedCombos(rollTotal).find(_.subsetOf(open))
}

object MaxTilesStrategy extends MoveStrategy {
  val maxTilesOrdering: Ordering[Move] = { // order by highest number of tiles closed and then highest numbers closed
    val bySizeDesc = Ordering.by[Move, Int](_.size).reverse
    val byMaxDesc = Ordering.by[Move, Int](_.max).reverse
    bySizeDesc.orElse(byMaxDesc)
  }

  val sortedCombos: Map[Roll, List[Move]] = MoveStrategy.combinations.view.mapValues(_.sorted(maxTilesOrdering)).toMap

  def chooseMove(open: State, rollTotal: Roll): Option[Move] = sortedCombos(rollTotal).find(_.subsetOf(open))
}

object MostOptionsStrategy extends MoveStrategy {
  def chooseMove(open: State, rollTotal: Roll): Option[Move] =
    MoveStrategy.combinations(rollTotal)
      .filter(_.subsetOf(open))
      .map { combo =>
        val remaining = open -- combo
        val score = (
          MoveStrategy.numValidRolls(remaining), // maximize future options
          combo.max,                             // then prefer higher tiles
          -combo.size                            // then prefer fewer tiles
        )
        combo -> score
      }
      .maxByOption(_._2)
      .map(_._1)
}

object HighestProbabilityStrategy extends MoveStrategy {
  def chooseMove(open: State, rollTotal: Roll): Option[Move] = ExactProbabilitySolver.bestMoves.get((open, rollTotal))
}
