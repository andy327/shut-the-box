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

  /** Precomputed map of sets of open tiles and the number of dice rolls that can be played in the next move */
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

trait HeuristicStrategy extends MoveStrategy {
  def stateOrdering: Ordering[State]

  /** Precomputed best move for every (open tiles, roll total) pair */
  lazy val decisionTree: Map[(State, Roll), Move] = {
    for {
      open <- MoveStrategy.allStates
      (rollTotal, moves) <- MoveStrategy.combinations
      validMoves = moves.filter(_.subsetOf(open))
      if validMoves.nonEmpty
      bestMove = validMoves.min(stateOrdering)
    } yield (open, rollTotal) -> bestMove
  }.toMap

  def chooseMove(open: State, rollTotal: Roll): Option[Move] = decisionTree.get((open, rollTotal))
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

object GreedyStrategy extends HeuristicStrategy {
  val stateOrdering: Ordering[State] = {
    val byMaxDesc = Ordering.by[Move, Int](_.max).reverse // prefer closing higher tile values
    val bySizeAsc = Ordering.by[Move, Int](_.size)        // prefer closing fewer tiles
    byMaxDesc.orElse(bySizeAsc)
  }
}

object MaxTilesStrategy extends HeuristicStrategy {
  val stateOrdering: Ordering[State] = {
    val bySizeDesc = Ordering.by[Move, Int](_.size).reverse // maximize number of tiles closed
    val byMaxDesc = Ordering.by[Move, Int](_.max).reverse   // prefer closing higher tile values
    bySizeDesc.orElse(byMaxDesc)
  }
}

object MostOptionsStrategy extends HeuristicStrategy {
  val stateOrdering: Ordering[State] = Ordering.by { combo: Move =>
    val remaining = MoveStrategy.allTiles -- combo
    (
      -MoveStrategy.numValidRolls(remaining), // maximize valid future rolls
      -combo.max,                             // prefer closing higher tile values
      combo.size                              // prefer closing fewer tiles
    )
  }
}

object HighestProbabilityStrategy extends MoveStrategy {
  def chooseMove(open: State, rollTotal: Roll): Option[Move] = ExactProbabilitySolver.bestMoves.get((open, rollTotal))
}
