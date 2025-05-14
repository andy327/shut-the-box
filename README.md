# Shut The Box – Scala Simulation with Strategy

This project is a Scala-based simulator and strategy benchmarking tool for the dice game **Shut the Box**.
It implements a variety of strategies, including an exact solver using dynamic programming, to analyze optimal play and win probabilities.

## Table of contents
* [🎲 Game Overview](#-game-overview)
* [🚀 Getting Started](#-getting-started)
* [📁 File Overview](#-file-overview)
* [🧩 Data Model](#-data-model)
* [🧠 Strategies](#-strategies)
* [✨ Future Improvements](#-future-improvements)
* [📄 License](#-license)

## 🎲 Game Overview

**Shut the Box** is a classic dice and numbers game typically played with tiles numbered 1 through 9.
At each turn, a player rolls two six-sided dice and selects a combination of remaining open tiles that sum to the total rolled.
These selected tiles are “shut” (removed), and the player continues until no valid moves remain.
The goal is to shut all tiles.

<p align="center">
<img src="https://imgur.com/TiCPmyl.png" alt="Shut the Box", width="400">
</p>

**Rules:**
- A roll of two dice yields a total between 2 and 12.
- The player must select a subset of remaining tiles that sum to the roll total.
- If no such subset exists, the game ends.
- The player wins only by shutting all the tiles.

## 🚀 Getting Started

### Requirements

- Scala 2.13.5
- sbt

### Running the Benchmark Tool

Clone and run:

```bash
git clone https://github.com/andy327/shut-the-box.git
cd shut-the-box
sbt "runMain com.andy327.stb.StrategyBenchmark"
```

## 📁 File Overview

The main logic for this project is implemented in the [`src/main/scala/com/andy327/stb`](https://github.com/andy327/shut-the-box/tree/main/src/main/scala/com/andy327/stb) directory.
Here's a breakdown of each file and its responsibilities:

| File | Description |
|------|-------------|
| [`Box.scala`](https://github.com/andy327/shut-the-box/blob/main/src/main/scala/com/andy327/stb/Box.scala) | Defines the rules and mechanics of playing Shut the Box and simulating a game using a strategy. |
| [`ExactProbabilitySolver.scala`](https://github.com/andy327/shut-the-box/blob/main/src/main/scala/com/andy327/stb/ExactProbabilitySolver.scala) | Computes exact win probabilities from any state using dynamic programming. Used by `HighestProbabilityStrategy`. |
| [`MoveStrategy.scala`](https://github.com/andy327/shut-the-box/blob/main/src/main/scala/com/andy327/stb/MoveStrategy.scala) | Defines the `MoveStrategy` trait and all strategy implementations: `RandomStrategy`, `GreedyStrategy`, `MaxTilesStrategy`, `MostOptionsStrategy`, and `HighestProbabilityStrategy`. Also includes shared helper structures like valid move combinations. |
| [`package.scala`](https://github.com/andy327/shut-the-box/blob/main/src/main/scala/com/andy327/stb/package.scala) | Defines type aliases for key concepts (`State`, `Roll`, `Move`). |
| [`ShutTheBox.scala`](https://github.com/andy327/shut-the-box/blob/main/src/main/scala/com/andy327/stb/ShutTheBox.scala) | Provides a simulation interface and entry point for running a game using a given strategy. |
| [`StrategyBenchmark.scala`](https://github.com/andy327/shut-the-box/blob/main/src/main/scala/com/andy327/stb/StrategyBenchmark.scala) | Benchmarks the win rates and execution times of all strategies across many simulated games. |

## 🧩 Data Model

The game is modeled using the following core types:

```scala
type Roll = Int
type State = Set[Int]
type Move = Set[Int]
```

### Game Components

- **`Box`**: Represents the game board, tracks open tiles, and applies moves.
- **`MoveStrategy`**: An abstraction that allows for easy experimentation and extension of strategies.
- **`ShutTheBox`**: Simulates complete games and runs benchmarks using strategies.
- **`StrategyBenchmark`**: Runs timed simulations across strategies and reports win rates.

### Box

The state of a game is determined completely by the set of remaining ("open") tiles left to flip.
The `Box` container case class wraps around this state, and provides a single method which proceeds to a possible new state given a roll total and a `MoveStrategy`:

```scala
def closeWith(rollTotal: Roll, strategy: MoveStrategy): Option[Box]
```

### MoveStrategy

The `MoveStrategy` trait contains just one method, which returns a set of tiles to close using the given roll total and set of currently open tiles, if one exists.

```scala
trait MoveStrategy {
  def chooseMove(open: State, rollTotal: Roll): Option[Move]
}
```

Strategies can be easily implemented by defining how to find the best possible move given the state and roll total.
For example, `GreedyStrategy`, which attempts to always close the largest tiles possible, can be defined as such:

```scala
object GreedyStrategy extends MoveStrategy {
  val greedyOrdering: Ordering[State] = {
    val byMaxDesc = Ordering.by[State, Int](_.max).reverse // prefer closing higher tile values
    val bySizeAsc = Ordering.by[State, Int](_.size)        // prefer closing fewer tiles
    byMaxDesc.orElse(bySizeAsc)
  }

  def chooseMove(open: State, rollTotal: Roll): Option[Move] = MoveStrategy.combinations(rollTotal)
    .filter(_.subsetOf(open))
    .maxByOption(open -- _)(greedyOrdering)
}
```

Here we define an `Ordering[State]`, which sorts the possible resulting states for a given roll to find the most desirable outcome.
A `List[Move]` is calculated for the given roll total, which is then filtered for those which are possible given the currently open tiles, and the remaining moves are sorted by their resulting state using the defined Ordering.

However, note that the overall space of possible states (`2^9 = 512`) and possible rolls (`11`) is relatively small.
This gives us an upper bound of `512 x 11 = 5,632` possible state and roll pairs.
When we filter out any rolls that immediately lead to no valid states, we are left with `4,040` valid state and roll pairs.
This is small enough that we can easily store the 'best move' given a state and roll total in memory.

### HeuristicStrategy

The `HeuristicStrategy` trait extends the `MoveStrategy`, instead defining a fully-resolved decision tree, and implementing the `chooseMove` method to retrieve the best move, using a given Ordering of States.
Instead of defining a function that chooses the best move given a state of open tiles and a roll total, we only require the user to define an `Ordering[State]`:

```scala
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
```

This simplifies our Strategy implementations to only require the definition for the Ordering, and speeds up our decision process by precomputing the best moves, cutting down on repetitive sorting.
The `GreedyStrategy` object now becomes:

```scala
object GreedyStrategy extends HeuristicStrategy {
  val stateOrdering: Ordering[State] = {
    val byMaxDesc = Ordering.by[Move, Int](_.max).reverse // prefer closing higher tile values
    val bySizeAsc = Ordering.by[Move, Int](_.size)        // prefer closing fewer tiles
    byMaxDesc.orElse(bySizeAsc)
  }
}
```

In benchmarks, the precomputed decision tree results in a 3x speedup.
In fact, the run-time complexity of our decision-making process is now `O(1)`, rather than linear with respect to the number of moves given the roll total.

## 🧠 Strategies

Each strategy implements the `MoveStrategy` trait and selects a move based on the current board state and roll.
Five strategies are implemented in the project, each with different priorities when deciding between valid moves.

### Implemented Strategies

- **RandomStrategy**: Selects a valid move randomly.
- **GreedyStrategy**: Prioritizes shutting high tiles and prefers fewer tiles.
- **MaxTilesStrategy**: Shuts the maximum number of tiles; breaks ties with tile values.
- **MostOptionsStrategy**: Chooses the move that leaves the highest number of valid roll outcomes.
- **HighestProbabilityStrategy**: Uses exact win probabilities to make decisions with the highest expected success.

The `GreedyStrategy` is a natural strategy to employ when playing the game; the player attempts to close the largest tiles possible when completing a move.
Often, this is an optimal move: choosing to close the 7 tile on a roll of 7 is strictly better than closing the 3 and 4 tiles.
Any later situation in which the player can close a 7 tile, they can choose to close the 3 and 4 instead.
The reverse is not necessarily true.

The `HighestProbabilityStrategy` forms an upper bound on the success rate.
It always selects a move that leads to the highest probability of success.
Interestingly, this doesn't always coincide with closing the highest tiles possible.
For example, the probability of a win with the tiles `[1,2,5]` open is 15.895%, and the probability of a win with the tiles `[1,3,4]` open is 15.818%.
Thus when faced with the open tiles 1 through 5 and a roll of 7, it's preferable to close tiles 3 and 4, rather than 2 and 5.

This leaves us with an interesting question: _what is generally the best strategy to use when playing the game, without having to memorize probabilities?_

It's hard to improve much on the strategy of closing the largest tiles possible.
However, it's worth noticing some of the situations where the optimal strategy differs.
In particular, there are some states of the game that are most likely to result in a win, and those states should be sought after in favor of other alternatives.
The game state with a remaining value of 8 that is most likely to result in a win is the game with open tiles `[1,2,5]`, at 15.895%.
Therefore, if we have the tiles 1 through 5 open and we roll a 7, we should opt to close the 3 and 4 tiles instead of the 2 and 5 tiles.

Some other interesting properties can be found by examining the decision tree built within the `HighestProbabilityStrategy`.
For example, what is the _most advantageous starting roll_ when playing a new game?

```scala
scala> (2 to 12)
     |   .map(roll => roll -> ExactProbabilitySolver.bestMoves((allTiles, roll)))
     |   .map { case (roll, bestMove) =>
     |     roll -> ExactProbabilitySolver.winProbabilities(allTiles -- bestMove)
     |   }
     |   .sortBy(-_._2)
     |   .foreach(println)
(9,0.10150185360563307)
(8,0.0843992883264558)
(12,0.0797040646327626)
(11,0.0727007112207658)
(7,0.07125306992568319)
(10,0.06943424590523688)
(6,0.06499269203919156)
(5,0.06108456955064155)
(4,0.05532663847072549)
(3,0.05106821208604296)
(2,0.045189824620113055)
```

As one might expect after playing a few rounds of Shut the Box, it hurts our chances to start with a roll of 2 or 3, as it removes the 1 and/or 2 tiles.
Generally, it's better to start with a higher roll to knock out some of the higher tiles that may be difficult to close later.
Only four starting rolls improve our chances over a fresh game: 9, 8, 12, and 11.
A starting roll of 9 gives a fairly significant boost to our odds, leaving an over 10% chance of success in closing all the tiles.

How high might our odds increase as we play the game?
We can again examine our decision tree more closely to look for maxima across all possible game states.

```scala
scala> ExactProbabilitySolver.winProbabilities
     |   .filter(_._2 < 1.0)
     |   .toList
     |   .sortBy(-_._2)
     |   .take(10)
     |   .foreach(println)
(HashSet(1, 2, 4),0.18209876543209874)
(HashSet(3, 4),0.1759259259259259)
(HashSet(5, 2),0.1728395061728395)
(HashSet(7),0.16666666666666666)
(HashSet(1, 6),0.16666666666666666)
(HashSet(5, 1, 2),0.15895061728395063)
(HashSet(1, 3, 4),0.15817901234567902)
(HashSet(2, 3, 4),0.1550925925925926)
(HashSet(5, 3),0.15123456790123457)
(HashSet(1, 2, 3, 4),0.14744084362139914)
```

As expected, games that are closer to the end (1-3 rolls away from a win) have a higher chance of success.
We can get as high as 18.210% when we are left with only the tiles `[1,2,4]`.
From that state, we have a `1/6` chance of closing immediately with a roll of 7, but if we roll any less than a 6, we are still able to win the game with one more roll.

While we can't improve upon the `HighestProbabilityStrategy` which always picks the move with the highest expected success, we can get pretty close by choosing to use a strategy that follows similar paths through the decision tree.
Any strategy that we come up with can be added to a new implementation of the `MoveStrategy` trait, by either defining the logic of choosing a new move or by specifying a heuristic for sorting game states, and we can see how well we do on average by testing it out in the strategy benchmark method.

## 📊 Strategy Benchmark

Each of the strategies is benchmarked by success rate using the `StrategyBenchmark` object.
The benchmark object employs a **Monte Carlo simulation** to assess the probability of a win by calculating the number of games won using the given strategy.
The `main` method runs a given number of simulations (default 1 million) using each strategy and outputs the win % along with the time taken to run the simulation.

### Output from 1M simulations:

```
Strategy Comparison (over 1000000 attempts):
Strategy             Win %      Time (s)
----------------------------------------
Random               2.047      2.311
Max Tiles            1.017      1.286
Greedy               6.953      1.976
Most Options         6.757      1.981
Highest Probability  7.139      1.998
----------------------------------------
True probability     7.143
```

The `HighestProbabilityStrategy` achieves the best performance, closely aligning with the true win probability of 7.143%.
We shouldn't read too much into the difference in run-times, given that inferior strategies will generally play a game more quickly (e.g., the Max Tiles strategy will eliminate smaller tiles, leading the player towards states with fewer options).

The benchmark tool operates by repeatedly calling the `playGame` method that returns a Boolean value indicating success by repeatedly applying the given `MoveStrategy` to simulate complete games.
This method is implemented as a tail-recursive function, allowing the compiler to reuse the stack frame after each move and improving our memory efficiency.

```scala
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
```

The function can be easily adapted to also return information about the series of moves played, for example tracking a `List[Move]` to track the moves made, or an `Int` to return the number of moves played.

An argument can be passed into the benchmark tool (`runMain com.andy327.stb.StrategyBenchmark <iterations>`) and we can perform the simulation using a different number of iterations than 1 million.

## ✨ Future Improvements

- Visualization or GUI for interactive play.
- Rule variants (e.g., more than 9 tiles, single die rules, multiple players, hidden information).
- Learning-based strategies using Monte Carlo Tree Search or reinforcement learning for more complex variants of the game.
- Strategy evolution using genetic algorithms (again, more applicable to variants where a precomputed decision tree is less viable).

## 📄 License

(c) 2025 Andres Perez.
This project is licensed under the [MIT License](https://opensource.org/licenses/MIT)
