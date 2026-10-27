package me.vladcip.guessthecountry.presentation

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import me.vladcip.guessthecountry.domain.model.Region
import me.vladcip.guessthecountry.domain.passport.PassportCover
import me.vladcip.guessthecountry.domain.passport.StampTier
import me.vladcip.guessthecountry.testing.FakeCountryRepository
import me.vladcip.guessthecountry.testing.FakePassportRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PassportViewModelTest {
    private val passport = FakePassportRepository()
    private val countries = FakeCountryRepository()
    private val day = LocalDate(2026, 10, 19)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PassportViewModel(countries, passport)

    private suspend fun ReceiveTurbine<PassportUiState>.awaitLoaded(): PassportUiState {
        while (true) {
            val item = awaitItem()
            if (!item.isLoading) return item
        }
    }

    @Test
    fun `needs a cover choice until one is set`() = runTest {
        val vm = viewModel()

        vm.state.test {
            val loaded = awaitLoaded()
            assertNull(loaded.cover)
            assertTrue(loaded.needsCoverChoice)

            vm.chooseCover(PassportCover.Navy)
            val chosen = awaitItem()
            assertEquals(PassportCover.Navy, chosen.cover)
            assertFalse(chosen.needsCoverChoice)
        }
    }

    @Test
    fun `is not asking for a cover while loading`() = runTest {
        val vm = viewModel()

        vm.state.test {
            val first = awaitItem()
            if (first.isLoading) assertFalse(first.needsCoverChoice)
        }
    }

    @Test
    fun `counts and tiles`() = runTest {
        passport.recordStamp("AB", StampTier.Entry, day)
        passport.recordStamp("AC", StampTier.FirstClue, day)
        val vm = viewModel()

        vm.state.test {
            val state = awaitLoaded()
            assertEquals(2, state.stampedCount)
            assertEquals(21, state.totalCountries)
            assertEquals(21, state.tiles.size)
            assertEquals(2, state.tiles.count { it.bestTier != null })
            assertEquals(StampTier.Entry, state.tiles.first { it.country.id == "AB" }.bestTier)
            assertEquals(state.tiles.map { it.country.name }.sorted(), state.tiles.map { it.country.name })
        }
    }

    @Test
    fun `region filter narrows tiles but not counts`() = runTest {
        passport.recordStamp("AB", StampTier.Entry, day)
        passport.recordStamp("AC", StampTier.Entry, day)
        val vm = viewModel()

        vm.state.test {
            awaitLoaded()

            vm.selectRegion(Region.Oceania)
            val oceania = awaitItem()
            assertEquals(Region.Oceania, oceania.selectedRegion)
            assertTrue(oceania.tiles.isNotEmpty())
            assertTrue(oceania.tiles.all { it.country.region == Region.Oceania })
            assertEquals(2, oceania.stampedCount)
            assertEquals(21, oceania.totalCountries)

            vm.selectRegion(null)
            val all = awaitItem()
            assertNull(all.selectedRegion)
            assertEquals(21, all.tiles.size)
        }
    }

    @Test
    fun `moments summarise milestones`() = runTest {
        val d1 = LocalDate(2026, 10, 1)
        val d2 = LocalDate(2026, 10, 2)
        val d3 = LocalDate(2026, 10, 3)
        val d4 = LocalDate(2026, 10, 4)
        passport.recordStamp("AB", StampTier.Entry, d1)
        passport.recordStamp("AC", StampTier.Entry, d3)
        passport.recordPostmark(d2)
        passport.recordPostmark(d4)
        val vm = viewModel()

        vm.state.test {
            assertEquals(Moments(2, d3, 2, d4), awaitLoaded().moments)
        }
    }

    @Test
    fun `moments are empty on a fresh passport`() = runTest {
        val vm = viewModel()

        vm.state.test {
            assertEquals(Moments(0, null, 0, null), awaitLoaded().moments)
        }
    }
}
