package com.andy327.stb

import collection.mutable.{Map => MMap}

/**
  * Calculates the optimal move for each possible roll and state.
  *
  * The solver uses dynamic programming with a top-down approach, building up a memoized cache of win probabilities and
  * most likely move to result in a win. Since the combinatorial size of the states being represented is relatively
  * small (2^9 = 512 states, with 11 possible roll totals), it is a straightforward recursive optimization problem.
  * Since tiles cannot be flipped back up, there are no cycles in the game, and so there are no issues with breaking a
  * state into easier sub-problems.
  */
object ExactProbabilitySolver {
  private val allStates: List[State] = MoveStrategy.allTiles.subsets().toList
  private val rollWeights = MoveStrategy.rollWeights.view.mapValues(_ / 36.0).toMap

  private val probabilityCache: MMap[State, Double] = MMap.empty
  private val bestMoveCache: MMap[(State, Roll), Move] = MMap.empty

  def winProbability(open: State): Double = probabilityCache.getOrElseUpdate(open, computeWinProbability(open))

  private def computeWinProbability(open: State): Double = {
    if (open.isEmpty) return 1.0

    val winProb = rollWeights.map { case (rollTotal, rollProb) =>
      val validMoves: List[Move] = MoveStrategy.combinations.getOrElse(rollTotal, Nil).filter(_.subsetOf(open))
      if (validMoves.isEmpty) 0.0
      else {
        val moveProbabilities: List[(Move, Double)] = validMoves.map(combo => combo -> winProbability(open -- combo))
        val (bestMove, bestProb): (Move, Double) = moveProbabilities.maxBy(_._2)
        bestMoveCache((open, rollTotal)) = bestMove
        bestProb * rollProb // probability is the win-probability of the best move with the given roll
      }
    }.sum

    probabilityCache(open) = winProb
    winProb
  }

  // precompute the best moves and probabilities for winning
  val winProbabilities: Map[State, Double] = allStates.map(state => state -> winProbability(state)).toMap
  lazy val bestMoves: Map[(State, Roll), Move] = bestMoveCache.toMap

  // probability of winning a starting game using an optimal strategy
  val startProbability = winProbabilities(MoveStrategy.allTiles)
}
