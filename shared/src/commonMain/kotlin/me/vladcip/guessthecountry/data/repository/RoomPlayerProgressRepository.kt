package me.vladcip.guessthecountry.data.repository

import me.vladcip.guessthecountry.data.db.PlayerProgressDao
import me.vladcip.guessthecountry.data.db.PlayerProgressEntity
import me.vladcip.guessthecountry.domain.progress.PlayerProgress
import me.vladcip.guessthecountry.domain.progress.PlayerProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class RoomPlayerProgressRepository(
    private val dao: PlayerProgressDao,
) : PlayerProgressRepository {
    override val progress: Flow<PlayerProgress> =
        dao.observe().map { it?.toDomain() ?: PlayerProgress() }

    override suspend fun recordGameStarted(today: LocalDate) = update { it.onGameStarted(today) }

    override suspend fun recordAdWatched() = update { it.onAdWatched() }

    private suspend fun update(transform: (PlayerProgress) -> PlayerProgress) {
        dao.update { current -> transform(current?.toDomain() ?: PlayerProgress()).toEntity() }
    }
}

private fun PlayerProgressEntity.toDomain() = PlayerProgress(
    gamesPlayed = gamesPlayed,
    freeGamesDay = freeGamesEpochDay?.let { LocalDate.fromEpochDays(it) },
    freeGamesUsed = freeGamesUsed,
    adCredits = adCredits,
    isPremium = isPremium,
)

private fun PlayerProgress.toEntity() = PlayerProgressEntity(
    gamesPlayed = gamesPlayed,
    freeGamesEpochDay = freeGamesDay?.toEpochDays(),
    freeGamesUsed = freeGamesUsed,
    adCredits = adCredits,
    isPremium = isPremium,
)
