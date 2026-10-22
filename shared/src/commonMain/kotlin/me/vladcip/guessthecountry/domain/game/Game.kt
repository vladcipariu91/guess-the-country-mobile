package me.vladcip.guessthecountry.domain.game

data class Game(
    val mode: GameMode,
    val rounds: List<GameRound>,
    val currentIndex: Int = 0,
) {
    init {
        require(rounds.isNotEmpty())
        require(currentIndex in rounds.indices)
    }

    val currentRound: GameRound get() = rounds[currentIndex]
    val roundNumber: Int get() = currentIndex + 1
    val totalRounds: Int get() = rounds.size
    val score: Int get() = rounds.sumOf { it.pointsEarned }
    val maxScore: Int get() = rounds.sumOf { it.maxPoints }
    val isLastRound: Boolean get() = currentIndex == rounds.lastIndex
    val isOver: Boolean get() = isLastRound && currentRound.isFinished

    fun updateCurrentRound(transform: (GameRound) -> GameRound): Game =
        copy(rounds = rounds.toMutableList().also { it[currentIndex] = transform(currentRound) })

    fun nextRound(): Game =
        if (currentRound.isFinished && !isLastRound) copy(currentIndex = currentIndex + 1) else this
}
