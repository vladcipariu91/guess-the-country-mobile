package me.vladcip.guessthecountry.presentation

import app.cash.turbine.test
import me.vladcip.guessthecountry.domain.passport.StampTier
import me.vladcip.guessthecountry.testing.FakeCountryRepository
import me.vladcip.guessthecountry.testing.FakePassportRepository
import me.vladcip.guessthecountry.testing.FakePlayerProgressRepository
import me.vladcip.guessthecountry.testing.FakeToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val progress = FakePlayerProgressRepository()
    private val today = FakeToday()
    private val passport = FakePassportRepository()
    private val countries = FakeCountryRepository()

    private fun homeViewModel() = HomeViewModel(progress, passport, countries, today)

    private fun expected(canPlay: Boolean, freeGamesLeft: Int, freeGamesUsedToday: Int = 3 - freeGamesLeft) = HomeUiState(
        canPlay = canPlay,
        freeGamesLeft = freeGamesLeft,
        totalCountries = 21,
        freeGamesUsedToday = freeGamesUsedToday,
        today = today.date,
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `play button turns into watch-ad once the free games are used`() = runTest {
        val vm = homeViewModel()

        vm.state.test {
            assertEquals(expected(canPlay = true, freeGamesLeft = 3), awaitItem())

            repeat(3) { progress.recordGameStarted(today.date) }
            assertEquals(expected(canPlay = true, freeGamesLeft = 2), awaitItem())
            assertEquals(expected(canPlay = true, freeGamesLeft = 1), awaitItem())
            assertEquals(expected(canPlay = false, freeGamesLeft = 0), awaitItem())

            progress.recordAdWatched()
            assertEquals(expected(canPlay = true, freeGamesLeft = 0, freeGamesUsedToday = 3), awaitItem())
        }
    }

    @Test
    fun `refresh picks up a new day`() = runTest {
        repeat(3) { progress.recordGameStarted(today.date) }
        val vm = homeViewModel()

        vm.state.test {
            assertEquals(expected(canPlay = false, freeGamesLeft = 0), awaitItem())

            today.advanceOneDay()
            vm.refresh()
            assertEquals(expected(canPlay = true, freeGamesLeft = 3), awaitItem())
        }
    }

    @Test
    fun `exposes stamp counts and today's used entries`() = runTest {
        listOf("AB", "AC", "AD").forEach { passport.recordStamp(it, StampTier.Entry, today.date) }
        repeat(2) { progress.recordGameStarted(today.date) }
        val vm = homeViewModel()

        vm.state.test {
            val state = awaitItem()
            assertEquals(3, state.stampedCount)
            assertEquals(21, state.totalCountries)
            assertEquals(2, state.freeGamesUsedToday)
            assertEquals(FakeToday().date, state.today)
        }
    }
}
