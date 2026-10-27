package me.vladcip.guessthecountry.data

import androidx.room.Room
import me.vladcip.guessthecountry.data.db.AppDatabase
import me.vladcip.guessthecountry.data.db.buildAppDatabase
import me.vladcip.guessthecountry.data.repository.RoomCountryRepository
import me.vladcip.guessthecountry.data.repository.RoomPassportRepository
import me.vladcip.guessthecountry.data.repository.RoomPlayerProgressRepository
import me.vladcip.guessthecountry.data.seed.DatabaseSeeder
import me.vladcip.guessthecountry.data.seed.SeedCountries
import me.vladcip.guessthecountry.domain.passport.CountryStamp
import me.vladcip.guessthecountry.domain.passport.PassportCover
import me.vladcip.guessthecountry.domain.passport.StampOutcome
import me.vladcip.guessthecountry.domain.passport.StampTier
import me.vladcip.guessthecountry.domain.progress.PlayerProgress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    private suspend fun passportRepository(): RoomPassportRepository {
        // country_stamps has a foreign key to countries, so seed them first.
        DatabaseSeeder(db.countryDao()).seedIfEmpty()
        return RoomPassportRepository(db.passportDao())
    }

    private fun days(count: Int) = today.plus(DatePeriod(days = count))

    @Test
    fun firstStampIsAFirstVisitAndNewBest() = runTest {
        val repository = passportRepository()

        val outcome = repository.recordStamp("JP", StampTier.Admitted, today)

        assertEquals(StampOutcome(StampTier.Admitted, isNewBest = true, isFirstVisit = true), outcome)
        assertEquals(listOf(CountryStamp("JP", StampTier.Admitted, today)), repository.stamps.first())
    }

    @Test
    fun betterTierUpgradesButKeepsFirstDate() = runTest {
        val repository = passportRepository()
        repository.recordStamp("JP", StampTier.Admitted, today)

        val outcome = repository.recordStamp("JP", StampTier.FirstClue, days(3))

        assertEquals(StampOutcome(StampTier.FirstClue, isNewBest = true, isFirstVisit = false), outcome)
        assertEquals(listOf(CountryStamp("JP", StampTier.FirstClue, today)), repository.stamps.first())
    }

    @Test
    fun lowerTierNeverDowngrades() = runTest {
        val repository = passportRepository()
        repository.recordStamp("JP", StampTier.Admitted, today)
        repository.recordStamp("JP", StampTier.FirstClue, days(3))

        val outcome = repository.recordStamp("JP", StampTier.Capital, days(4))

        assertEquals(StampOutcome(StampTier.Capital, isNewBest = false, isFirstVisit = false), outcome)
        assertEquals(listOf(CountryStamp("JP", StampTier.FirstClue, today)), repository.stamps.first())
    }

    @Test
    fun postmarkOncePerDay() = runTest {
        val repository = passportRepository()

        assertTrue(repository.recordPostmark(today))
        assertFalse(repository.recordPostmark(today))
        assertTrue(repository.recordPostmark(days(1)))

        assertEquals(listOf(today, days(1)), repository.postmarks.first())
    }

    @Test
    fun coverIsNullUntilChosen() = runTest {
        val repository = passportRepository()

        assertNull(repository.cover.first())

        repository.setCover(PassportCover.Green)
        assertEquals(PassportCover.Green, repository.cover.first())
    }
}
