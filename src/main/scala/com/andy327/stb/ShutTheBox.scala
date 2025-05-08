package com.andy327.stb

import scala.annotation.tailrec
import scala.util.Random

case class Dice(n: Int = 2) {
  private val rand = new Random
  def roll: Int = List.fill(n)(rand.nextInt(6) + 1).sum
}

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

case class Box(closed: Set[Int] = Set.empty) {
  val isShut: Boolean = closed.size == 9
  val open: Set[Int] = MoveStrategy.allTiles -- closed

  def closeWith(amt: Int, stategy: MoveStrategy): Option[Box] =
    stategy.chooseMove(open, amt).map(closing => Box(closed ++ closing))
}

object ShutTheBox {
  private val dice = Dice(2)

  /** Plays the game to completion and returns true if all tiles are closed */
  def playGame(stategy: MoveStrategy): Boolean = {
    @tailrec
    def playGameTailrec(box: Option[Box]): Boolean = box match {
      case Some(b) if b.isShut => true
      case Some(b) => playGameTailrec(b.closeWith(dice.roll, stategy))
      case None => false
    }

    playGameTailrec(Some(Box()))
  }

  def winFraction(attempts: Int, stategy: MoveStrategy): Double = {
    val start = System.currentTimeMillis
    val wins = (1 to attempts).count(_ => playGame(stategy))
    val end = System.currentTimeMillis
    val elapsedTime = (end - start) / 1000.0
    println(f"Won $wins games out of $attempts attempts (${wins * 100.0 / attempts}%.3f%%)")
    println(f"Time taken: $elapsedTime%.3fs")
    wins.toDouble / attempts
  }

  def runBenchmark(attempts: Int): Unit = {
    val strategies = Map(
      "Random" -> RandomStrategy,
      "Greedy" -> GreedyStrategy,
      "Max Tiles" -> MaxTilesStrategy
    )

    StrategyBenchmark.compareStrategies(attempts, strategies)
  }
}

object StrategyBenchmark {
  def compareStrategies(attempts: Int, strategies: Map[String, MoveStrategy]): Unit = {
    println(s"Strategy Comparison (over $attempts attempts):")
    println("%-20s %-10s %-10s".format("Strategy", "Win %", "Time (s)"))
    println("-" * 40)

    strategies.foreach { case (name, strategy) =>
      val start = System.currentTimeMillis
      val wins = (1 to attempts).count(_ => ShutTheBox.playGame(strategy))
      val elapsed = (System.currentTimeMillis - start) / 1000.0
      val winRate = wins * 100.0 / attempts
      println("%-20s %-10.3f %-10.3f".format(name, winRate, elapsed))
    }
  }
}
