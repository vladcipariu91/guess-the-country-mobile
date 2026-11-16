package me.vladcip.guessthecountry.presentation

import app.cash.turbine.test
import me.vladcip.guessthecountry.domain.passport.PassportProgress
import me.vladcip.guessthecountry.domain.passport.StampTier
import me.vladcip.guessthecountry.domain.progress.PlayerProgress
import me.vladcip.guessthecountry.testing.FakeCountryRepository
import me.vladcip.guessthecountry.testing.FakePassportRepository
import me.vladcip.guessthecountry.testing.FakePlayerProgressRepository
import me.vladcip.guessthecountry.testing.FakeToday
import me.vladcip.guessthecountry.testing.testCountries
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private var progress = FakePlayerProgressRepository()
    private val today = FakeToday()
    private val passport = FakePassportRepository()
    private var countries = FakeCountryRepository()

    private fun homeViewModel() = HomeViewModel(progress, passport, countries, today)

    private fun expected(
        canPlay: Boolean,
        freeGamesLeft: Int,
        freeGamesUsedToday: Int = 3 - freeGamesLeft,
        passportProgress: PassportProgress? = PassportProgress(0, 21),
    ) = HomeUiState(
        canPlay = canPlay,
        freeGamesLeft = freeGamesLeft,
        totalCountries = 21,
        freeGamesUsedToday = freeGamesUsedToday,
        today = today.date,
        passportProgress = passportProgress,
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

    @Test
    fun `new player sees zero of all countries`() = runTest {
        val vm = homeViewModel()

        vm.state.test {
            val progress = expectMostRecentItem().passportProgress
            assertEquals(PassportProgress(0, 21), progress)
            assertEquals(0, progress?.percent)
        }
    }

    @Test
    fun `player with every country stamped sees 100 percent`() = runTest {
        testCountries(21).forEach { passport.recordStamp(it.id, StampTier.Entry, today.date) }
        val vm = homeViewModel()

        vm.state.test {
            val progress = expectMostRecentItem().passportProgress
            assertEquals(PassportProgress(21, 21), progress)
            assertEquals(100, progress?.percent)
        }
    }

    @Test
    fun `a new stamp raises the count without recreating the view model`() = runTest {
        val vm = homeViewModel()

        vm.state.test {
            assertEquals(0, expectMostRecentItem().passportProgress?.stamped)

            passport.recordStamp(testCountries(1).single().id, StampTier.Entry, today.date)
            assertEquals(1, expectMostRecentItem().passportProgress?.stamped)
        }
    }

    @Test
    fun `a better stamp on a stamped country keeps the count`() = runTest {
        val id = testCountries(1).single().id
        passport.recordStamp(id, StampTier.Entry, today.date)
        val vm = homeViewModel()

        vm.state.test {
            assertEquals(1, expectMostRecentItem().passportProgress?.stamped)

            passport.recordStamp(id, StampTier.FirstClue, today.date)
            assertEquals(StampTier.FirstClue, passport.stamps.value.single().bestTier)
            // The state is unchanged, so StateFlow emits nothing new.
            expectNoEvents()
            assertEquals(1, vm.state.value.passportProgress?.stamped)
        }
    }

    @Test
    fun `progress is hidden when countries fail to load`() = runTest {
        countries = FakeCountryRepository(error = RuntimeException("no countries"))
        val vm = homeViewModel()

        vm.state.test {
            val state = expectMostRecentItem()
            assertNull(state.passportProgress)
            assertEquals(0, state.totalCountries)
            assertEquals(true, state.canPlay)
            assertEquals(3, state.freeGamesLeft)
        }
    }

    @Test
    fun `progress is shown for premium and out-of-games players`() = runTest {
        progress = FakePlayerProgressRepository(PlayerProgress(isPremium = true))
        homeViewModel().state.test {
            val state = expectMostRecentItem()
            assertEquals(true, state.isPremium)
            assertNotNull(state.passportProgress)
        }

        progress = FakePlayerProgressRepository()
        repeat(3) { progress.recordGameStarted(today.date) }
        homeViewModel().state.test {
            val state = expectMostRecentItem()
            assertEquals(false, state.canPlay)
            assertEquals(PassportProgress(0, 21), state.passportProgress)
        }
    }
}
