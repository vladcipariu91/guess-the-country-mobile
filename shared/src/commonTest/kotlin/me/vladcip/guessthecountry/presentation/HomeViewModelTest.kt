package me.vladcip.guessthecountry.presentation

import app.cash.turbine.test
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

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `play button turns into watch-ad once the free games are used`() = runTest {
        val vm = HomeViewModel(progress, today)

        vm.state.test {
            assertEquals(HomeUiState(canPlay = true, freeGamesLeft = 3), awaitItem())

            repeat(3) { progress.recordGameStarted(today.date) }
            assertEquals(HomeUiState(canPlay = true, freeGamesLeft = 2), awaitItem())
            assertEquals(HomeUiState(canPlay = true, freeGamesLeft = 1), awaitItem())
            assertEquals(HomeUiState(canPlay = false, freeGamesLeft = 0), awaitItem())

            progress.recordAdWatched()
            assertEquals(HomeUiState(canPlay = true, freeGamesLeft = 0), awaitItem())
        }
    }

    @Test
    fun `refresh picks up a new day`() = runTest {
        repeat(3) { progress.recordGameStarted(today.date) }
        val vm = HomeViewModel(progress, today)

        vm.state.test {
            assertEquals(HomeUiState(canPlay = false, freeGamesLeft = 0), awaitItem())

            today.advanceOneDay()
            vm.refresh()
            assertEquals(HomeUiState(canPlay = true, freeGamesLeft = 3), awaitItem())
        }
    }
}
