package com.andy327.stb

/**
  * Container for tiles numbered 1 through 9 that can be either open or closed.
  *
  * A Box with all closed tiles has been successfully completed (a win). Different strategies can be employed for
  * selecting which tile(s) to close using a given dice roll total.
  */
case class Box(closed: Set[Int] = Set.empty) {
  val isShut: Boolean = closed.size == 9
  val open: Set[Int] = MoveStrategy.allTiles -- closed

  def closeWith(amt: Int, stategy: MoveStrategy): Option[Box] =
    stategy.chooseMove(open, amt).map(closing => Box(closed ++ closing))
}
