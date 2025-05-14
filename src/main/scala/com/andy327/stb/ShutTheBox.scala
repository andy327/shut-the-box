package com.andy327.stb

import scala.annotation.tailrec

case class Dice(n: Int = 2) {
  private val rand = new scala.util.Random
  private val buffer = new Array[Int](n)
  def roll: Roll = buffer.map(_ => rand.nextInt(6) + 1).sum
}

object ShutTheBox {
  private val dice = Dice(2)

  /** Plays the game to completion and returns true if all tiles are closed */
  def playGame(strategy: MoveStrategy): Boolean = {
    @tailrec
    def playGameTailrec(box: Option[Box]): Boolean = box match {
      case Some(b) if b.isShut => true
      case Some(b) => playGameTailrec(b.closeWith(dice.roll, strategy))
      case None => false
    }

    playGameTailrec(Some(Box()))
  }

  /** Runs a Monte Carlo simulation to calculate the percentage of games won using a given MoveStrategy. */
  def winFraction(attempts: Int, strategy: MoveStrategy): Double = {
    val start = System.currentTimeMillis
    val wins = (1 to attempts).count(_ => playGame(strategy))
    val end = System.currentTimeMillis
    val elapsedTime = (end - start) / 1000.0
    println(f"Won $wins games out of $attempts attempts (${wins * 100.0 / attempts}%.3f%%)")
    println(f"Time taken: $elapsedTime%.3fs")
    wins.toDouble / attempts
  }
}
