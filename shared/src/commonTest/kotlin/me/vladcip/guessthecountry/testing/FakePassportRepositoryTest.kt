package me.vladcip.guessthecountry.testing

import me.vladcip.guessthecountry.domain.passport.CountryStamp
import me.vladcip.guessthecountry.domain.passport.PassportCover
import me.vladcip.guessthecountry.domain.passport.StampOutcome
import me.vladcip.guessthecountry.domain.passport.StampTier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pins the fake to the same rules as the Room implementation. */
class FakePassportRepositoryTest {
    private val repository = FakePassportRepository()
    private val today = LocalDate(2026, 10, 19)

    private fun days(count: Int) = today.plus(DatePeriod(days = count))

    @Test
    fun firstStampIsAFirstVisitAndNewBest() = runTest {
        val outcome = repository.recordStamp("JP", StampTier.Admitted, today)

        assertEquals(StampOutcome(StampTier.Admitted, isNewBest = true, isFirstVisit = true), outcome)
        assertEquals(listOf(CountryStamp("JP", StampTier.Admitted, today)), repository.stamps.first())
    }

    @Test
    fun betterTierUpgradesButKeepsFirstDate() = runTest {
        repository.recordStamp("JP", StampTier.Admitted, today)

        val outcome = repository.recordStamp("JP", StampTier.FirstClue, days(3))

        assertEquals(StampOutcome(StampTier.FirstClue, isNewBest = true, isFirstVisit = false), outcome)
        assertEquals(listOf(CountryStamp("JP", StampTier.FirstClue, today)), repository.stamps.first())
    }

    @Test
    fun lowerTierNeverDowngrades() = runTest {
        repository.recordStamp("JP", StampTier.Admitted, today)
        repository.recordStamp("JP", StampTier.FirstClue, days(3))

        val outcome = repository.recordStamp("JP", StampTier.Capital, days(4))

        assertEquals(StampOutcome(StampTier.Capital, isNewBest = false, isFirstVisit = false), outcome)
        assertEquals(listOf(CountryStamp("JP", StampTier.FirstClue, today)), repository.stamps.first())
    }

    @Test
    fun postmarkOncePerDay() = runTest {
        assertTrue(repository.recordPostmark(today))
        assertFalse(repository.recordPostmark(today))
        assertTrue(repository.recordPostmark(days(1)))

        assertEquals(listOf(today, days(1)), repository.postmarks.first())
    }

    @Test
    fun coverIsNullUntilChosen() = runTest {
        assertNull(repository.cover.first())

        repository.setCover(PassportCover.Green)
        assertEquals(PassportCover.Green, repository.cover.first())
    }
}
