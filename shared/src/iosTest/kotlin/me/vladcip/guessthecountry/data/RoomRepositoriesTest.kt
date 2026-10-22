package me.vladcip.guessthecountry.data

import androidx.room.Room
import me.vladcip.guessthecountry.data.db.AppDatabase
import me.vladcip.guessthecountry.data.db.buildAppDatabase
import me.vladcip.guessthecountry.data.repository.RoomCountryRepository
import me.vladcip.guessthecountry.data.repository.RoomPlayerProgressRepository
import me.vladcip.guessthecountry.data.seed.DatabaseSeeder
import me.vladcip.guessthecountry.data.seed.SeedCountries
import me.vladcip.guessthecountry.domain.progress.PlayerProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Runs against a real in-memory Room database (Kotlin/Native, bundled SQLite). */
class RoomRepositoriesTest {
    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>().buildAppDatabase()
    private val today = LocalDate(2026, 10, 19)

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun seedsTheDatabaseOnFirstUseWithHintsInOrder() = runTest {
        val repository = RoomCountryRepository(db.countryDao(), DatabaseSeeder(db.countryDao()))

        val countries = repository.getAllCountries()

        assertEquals(SeedCountries.all.sortedBy { it.id }, countries.sortedBy { it.id })
    }

    @Test
    fun seedingTwiceDoesNotDuplicateRows() = runTest {
        DatabaseSeeder(db.countryDao()).seedIfEmpty()
        DatabaseSeeder(db.countryDao()).seedIfEmpty()

        assertEquals(SeedCountries.all.size, db.countryDao().count())
    }

    @Test
    fun progressDefaultsToAFreshFreePlayer() = runTest {
        val repository = RoomPlayerProgressRepository(db.playerProgressDao())

        assertEquals(PlayerProgress(), repository.progress.first())
    }

    @Test
    fun persistsDailyFreeGamesAndAdCredits() = runTest {
        val repository = RoomPlayerProgressRepository(db.playerProgressDao())

        repeat(3) { repository.recordGameStarted(today) }
        val limited = repository.progress.first()
        assertEquals(today, limited.freeGamesDay)
        assertEquals(3, limited.gamesPlayed)
        assertFalse(limited.canStartGame(today))

        repository.recordAdWatched()
        assertTrue(repository.progress.first().canStartGame(today))
        repository.recordGameStarted(today)
        assertEquals(0, repository.progress.first().adCredits)
    }
}
