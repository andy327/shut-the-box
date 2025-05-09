package com.andy327.stb

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

    println("-" * 40)
    println("%-20s %-10.3f".format("True probability", ExactProbabilitySolver.startProbability * 100.0))
  }

  def runBenchmark(attempts: Int): Unit = {
    val strategies = Map(
      "Random" -> RandomStrategy,
      "Greedy" -> GreedyStrategy,
      "Max Tiles" -> MaxTilesStrategy,
      "Most Options" -> MostOptionsStrategy,
      "Highest Probability" -> HighestProbabilityStrategy
    )

    StrategyBenchmark.compareStrategies(attempts, strategies)
  }

  def main(args: Array[String]): Unit = {
    val attempts = if (args.nonEmpty) args(0).toIntOption.getOrElse(1000000) else 1000000
    runBenchmark(attempts)
  }
}
