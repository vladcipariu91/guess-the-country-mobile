package me.vladcip.guessthecountry.presentation

import me.vladcip.guessthecountry.domain.game.GameFactory
import me.vladcip.guessthecountry.domain.game.GameMode
import me.vladcip.guessthecountry.domain.game.HintRound
import me.vladcip.guessthecountry.domain.game.RoundPhase
import me.vladcip.guessthecountry.domain.passport.CountryStamp
import me.vladcip.guessthecountry.domain.passport.StampOutcome
import me.vladcip.guessthecountry.domain.passport.StampTier
import me.vladcip.guessthecountry.domain.progress.PlayerProgress
import me.vladcip.guessthecountry.testing.FakePassportRepository
import me.vladcip.guessthecountry.testing.FakeCountryRepository
import me.vladcip.guessthecountry.testing.FakePlayerProgressRepository
import me.vladcip.guessthecountry.testing.FakeToday
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDate
import me.vladcip.guessthecountry.domain.passport.PassportRepository
import me.vladcip.guessthecountry.ads.RewardedAdResult
import me.vladcip.guessthecountry.testing.FakeAdService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private val countries = FakeCountryRepository()
    private val progress = FakePlayerProgressRepository()
    private val today = FakeToday()
    private val ads = FakeAdService()
    private val passport = FakePassportRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(repository: PassportRepository = passport) =
        GameViewModel(countries, progress, repository, GameFactory(random = Random(1)), today, ads)

    /** Passport whose stamp writes suspend until [release] is called, to exercise the stale-game guards. */
    private class GatedPassport(private val inner: FakePassportRepository) : PassportRepository by inner {
        private val gate = CompletableDeferred<Unit>()

        fun release() {
            gate.complete(Unit)
        }

        override suspend fun recordStamp(countryId: String, tier: StampTier, day: LocalDate): StampOutcome {
            gate.await()
            return inner.recordStamp(countryId, tier, day)
        }
    }

    /** Passport whose stamp writes fail, like a full disk would. */
    private class FailingPassport(private val inner: FakePassportRepository) : PassportRepository by inner {
        override suspend fun recordStamp(countryId: String, tier: StampTier, day: LocalDate): StampOutcome =
            throw IllegalStateException("disk full")
    }

    /** Wins the first six rounds and the last one without continuing past it. */
    private fun GameViewModel.winUntilLastRoundGuessed() {
        repeat(6) {
            guess(playing.game.currentRound.country)
            continueGame()
        }
        guess(playing.game.currentRound.country)
    }

    private val GameViewModel.playing get() = assertIs<GameUiState.Playing>(state.value)

    private val GameViewModel.hintRound get() = assertIs<HintRound>(playing.game.currentRound)

    private fun GameViewModel.playWholeGame() {
        repeat(7) {
            guess(playing.game.currentRound.country)
            continueGame()
        }
    }

    private fun GameViewModel.startNewGame() = startNewGame(GameMode.GuessTheCountry)

    private fun GameViewModel.useFreeGames() = repeat(PlayerProgress.FREE_GAMES_PER_DAY) {
        startNewGame()
        exitToMenu()
    }

    @Test
    fun `starts idle on the main menu`() {
        assertEquals(GameUiState.Idle, viewModel().state.value)
    }

    @Test
    fun `starting a game deals 7 rounds and uses a free game`() {
        val vm = viewModel()
        vm.startNewGame()

        assertEquals(7, vm.playing.game.totalRounds)
        assertEquals(2, progress.progress.value.freeGamesLeft(today.date))
    }

    @Test
    fun `guess and clue actions update the current round`() {
        val vm = viewModel()
        vm.startNewGame()
        val round = vm.playing.game.currentRound
        val wrong = round.options.first { it != round.country }

        vm.guess(wrong)
        assertEquals(4, vm.hintRound.pointsAvailable)
        vm.nextClue()
        assertEquals(3, vm.hintRound.pointsAvailable)
        vm.skipToCapital()
        assertEquals(RoundPhase.Capital, vm.hintRound.phase)
        vm.guess(round.country)
        assertEquals(1, vm.playing.game.score)
    }

    @Test
    fun `continue does nothing until the round is finished`() {
        val vm = viewModel()
        vm.startNewGame()
        vm.continueGame()
        assertEquals(1, vm.playing.game.roundNumber)
    }

    @Test
    fun `perfect game finishes with 35 out of 35 then returns to the menu`() {
        val vm = viewModel()
        vm.startNewGame()
        vm.playWholeGame()
        val summary = assertIs<GameUiState.Finished>(vm.state.value).summary
        assertEquals(GameMode.GuessTheCountry, summary.mode)
        assertEquals(35, summary.score)
        assertEquals(35, summary.maxScore)

        vm.exitToMenu()
        assertEquals(GameUiState.Idle, vm.state.value)
    }

    @Test
    fun `quitting goes straight back to the main menu without an ad`() {
        val vm = viewModel()
        vm.startNewGame()
        vm.exitToMenu()

        assertEquals(GameUiState.Idle, vm.state.value)
        vm.startNewGame()
        assertIs<GameUiState.Playing>(vm.state.value)
    }

    @Test
    fun `ads are initialized when the app starts`() {
        viewModel()
        assertEquals(1, ads.initializeCalls)
    }

    @Test
    fun `free games do not show ads`() {
        val vm = viewModel()
        vm.useFreeGames()
        assertEquals(0, ads.adsShown)
    }

    @Test
    fun `after 3 free games the next game plays an ad first`() {
        val vm = viewModel()
        vm.useFreeGames()

        vm.startNewGame()
        assertEquals(GameUiState.ShowingAd(GameMode.GuessTheCountry), vm.state.value)
        assertEquals(1, ads.adsShown)
    }

    @Test
    fun `watching the ad to the end starts that game and unlocks only that game`() {
        val vm = viewModel()
        vm.useFreeGames()

        vm.watchAd(GameMode.GuessTheFlag)
        assertEquals(GameUiState.ShowingAd(GameMode.GuessTheFlag), vm.state.value)
        ads.finishAd(RewardedAdResult.Rewarded)
        assertEquals(GameMode.GuessTheFlag, vm.playing.game.mode)
        assertEquals(0, progress.progress.value.adCredits)

        vm.exitToMenu()
        vm.startNewGame()
        assertEquals(GameUiState.ShowingAd(GameMode.GuessTheCountry), vm.state.value)
    }

    @Test
    fun `closing the ad early goes back to the menu without a game`() {
        val vm = viewModel()
        vm.useFreeGames()

        vm.watchAd(GameMode.GuessTheCountry)
        ads.finishAd(RewardedAdResult.Dismissed)

        assertEquals(GameUiState.Idle, vm.state.value)
        assertEquals(0, progress.progress.value.adCredits)
        assertEquals(3, progress.progress.value.gamesPlayed)
    }

    @Test
    fun `if no ad is available the player still gets the game`() {
        val vm = viewModel()
        vm.useFreeGames()

        vm.watchAd(GameMode.GuessTheCountry)
        ads.finishAd(RewardedAdResult.Unavailable)

        assertEquals(GameMode.GuessTheCountry, vm.playing.game.mode)
        assertEquals(4, progress.progress.value.gamesPlayed)
    }

    @Test
    fun `leaving the ad screen returns to the menu`() {
        val vm = viewModel()
        vm.useFreeGames()

        vm.watchAd(GameMode.GuessTheCountry)
        vm.exitToMenu()
        assertEquals(GameUiState.Idle, vm.state.value)
    }

    @Test
    fun `free games come back the next day`() {
        val vm = viewModel()
        vm.useFreeGames()

        today.advanceOneDay()
        vm.startNewGame()
        assertIs<GameUiState.Playing>(vm.state.value)
    }

    @Test
    fun `premium player is never shown an ad`() {
        progress.progress.value = PlayerProgress(isPremium = true)
        val vm = viewModel()
        repeat(5) {
            vm.startNewGame()
            vm.playWholeGame()
            vm.exitToMenu()
        }
        assertEquals(5, progress.progress.value.gamesPlayed)
    }

    @Test
    fun `repository failure shows an error and does not use a free game`() {
        countries.error = IllegalStateException("db broken")
        val vm = viewModel()
        vm.startNewGame()

        assertEquals(GameUiState.Error(GameMode.GuessTheCountry, "db broken"), vm.state.value)
        assertEquals(0, progress.progress.value.gamesPlayed)
        assertTrue(progress.progress.value.canStartGame(today.date))
    }

    @Test
    fun `flag game deals 7 one-point rounds and shares the daily free games`() {
        val vm = viewModel()
        vm.startNewGame(GameMode.GuessTheFlag)
        assertEquals(GameMode.GuessTheFlag, vm.playing.game.mode)
        assertEquals(7, vm.playing.game.maxScore)

        vm.guess(vm.playing.game.currentRound.options.first { it != vm.playing.game.currentRound.country })
        assertEquals(0, vm.playing.game.score)
        assertTrue(vm.playing.game.currentRound.isFinished)
        assertEquals(2, progress.progress.value.freeGamesLeft(today.date))
    }

    @Test
    fun `clue actions do nothing in the flag game`() {
        val vm = viewModel()
        vm.startNewGame(GameMode.GuessTheFlag)
        val before = vm.playing.game
        vm.nextClue()
        vm.skipToCapital()
        assertEquals(before, vm.playing.game)
    }

    private val GameViewModel.finished get() = assertIs<GameUiState.Finished>(state.value).summary

    @Test
    fun `winning a hint round stamps the country immediately`() {
        val vm = viewModel()
        vm.startNewGame()
        val country = vm.playing.game.currentRound.country

        vm.guess(country)

        assertEquals(listOf(CountryStamp(country.id, StampTier.FirstClue, today.date)), passport.stamps.value)
        assertEquals(StampOutcome(StampTier.FirstClue, isNewBest = true, isFirstVisit = true), vm.playing.stamps[0])
    }

    @Test
    fun `losing a hint round records nothing`() {
        val vm = viewModel()
        vm.startNewGame()
        vm.skipToCapital()
        val round = vm.playing.game.currentRound
        vm.guess(round.options.first { it != round.country })

        assertTrue(vm.playing.game.currentRound.isFinished)
        assertTrue(passport.stamps.value.isEmpty())
        assertTrue(vm.playing.stamps.isEmpty())
    }

    @Test
    fun `flag rounds never stamp`() {
        val vm = viewModel()
        vm.startNewGame(GameMode.GuessTheFlag)
        vm.playWholeGame()

        assertTrue(passport.stamps.value.isEmpty())
        assertTrue(vm.finished.rounds.all { it.stamp == null })
    }

    @Test
    fun `quitting keeps earned stamps`() {
        val vm = viewModel()
        vm.startNewGame()
        val country = vm.playing.game.currentRound.country
        vm.guess(country)
        vm.exitToMenu()

        assertEquals(listOf(country.id), passport.stamps.value.map { it.countryId })

        vm.startNewGame()
        vm.playWholeGame()
        assertEquals(7, vm.finished.rounds.size)
    }

    @Test
    fun `summary lists rounds and first visits and score`() {
        val vm = viewModel()
        vm.startNewGame()
        vm.playWholeGame()

        val summary = vm.finished
        assertEquals(7, summary.rounds.size)
        assertEquals(summary.rounds.map { it.country }, summary.firstVisits)
        assertEquals(7, summary.firstVisits.size)
        assertEquals(35, summary.score)
        assertEquals(35, summary.maxScore)
        assertEquals(today.date, summary.playedOn)
        assertNull(summary.postmark)
    }

    @Test
    fun `third free game earns the postmark even if quit`() {
        val vm = viewModel()
        vm.useFreeGames()

        assertEquals(listOf(today.date), passport.postmarks.value)
    }

    @Test
    fun `postmark shows on the summary of the game that earned it`() {
        val vm = viewModel()
        repeat(2) {
            vm.startNewGame()
            vm.exitToMenu()
        }
        assertTrue(passport.postmarks.value.isEmpty())

        vm.startNewGame()
        vm.playWholeGame()

        assertEquals(today.date, vm.finished.postmark)
    }

    @Test
    fun `ad game earns no postmark`() {
        val vm = viewModel()
        vm.useFreeGames()

        vm.watchAd(GameMode.GuessTheCountry)
        ads.finishAd(RewardedAdResult.Rewarded)

        assertEquals(listOf(today.date), passport.postmarks.value)
    }

    @Test
    fun `premium earns no postmark`() {
        progress.progress.value = PlayerProgress(isPremium = true)
        val vm = viewModel()
        vm.useFreeGames()
        assertTrue(passport.postmarks.value.isEmpty())

        vm.startNewGame()
        vm.guess(vm.playing.game.currentRound.country)
        assertEquals(1, passport.stamps.value.size)
    }

    @Test
    fun `postmark again on a new day`() {
        val vm = viewModel()
        vm.useFreeGames()
        val day1 = today.date
        today.advanceOneDay()
        vm.useFreeGames()

        assertEquals(listOf(day1, today.date), passport.postmarks.value)
    }

    @Test
    fun `a failed stamp write leaves the round without a stamp and play continues`() {
        val vm = viewModel(FailingPassport(passport))
        vm.startNewGame()
        vm.guess(vm.playing.game.currentRound.country)

        assertTrue(vm.playing.game.currentRound.isWon)
        assertTrue(vm.playing.stamps.isEmpty())
        assertTrue(passport.stamps.value.isEmpty())

        vm.continueGame()
        assertEquals(2, vm.playing.game.roundNumber)
        vm.playWholeGameFromHere()
        assertTrue(vm.finished.rounds.all { it.stamp == null })
        assertEquals(35, vm.finished.score)
    }

    private fun GameViewModel.playWholeGameFromHere() {
        repeat(7 - playing.game.currentIndex) {
            guess(playing.game.currentRound.country)
            continueGame()
        }
    }

    @Test
    fun `a stamp that finishes saving after quitting is stored but not shown in the next game`() {
        val gated = GatedPassport(passport)
        val vm = viewModel(gated)
        vm.startNewGame()
        val country = vm.playing.game.currentRound.country
        vm.guess(country)
        vm.exitToMenu()
        vm.startNewGame()

        gated.release()

        assertTrue(vm.playing.stamps.isEmpty())
        assertEquals(listOf(country.id), passport.stamps.value.map { it.countryId })
    }

    @Test
    fun `results wait for the last stamp to be saved`() {
        val gated = GatedPassport(passport)
        val vm = viewModel(gated)
        vm.startNewGame()
        vm.winUntilLastRoundGuessed()

        vm.continueGame()
        assertIs<GameUiState.Playing>(vm.state.value)

        gated.release()

        val summary = vm.finished
        assertEquals(7, summary.rounds.size)
        assertTrue(summary.rounds.all { it.stamp != null })
        assertEquals(7, summary.firstVisits.size)
    }

    @Test
    fun `quitting while the last stamp is saving never shows the results`() {
        val gated = GatedPassport(passport)
        val vm = viewModel(gated)
        vm.startNewGame()
        vm.winUntilLastRoundGuessed()
        vm.continueGame()

        vm.exitToMenu()
        gated.release()

        assertEquals(GameUiState.Idle, vm.state.value)
        assertEquals(7, passport.stamps.value.size)
    }

    @Test
    fun `quitting right after winning the last round never shows the results`() {
        val gated = GatedPassport(passport)
        val vm = viewModel(gated)
        vm.startNewGame()
        vm.winUntilLastRoundGuessed()

        vm.exitToMenu()
        gated.release()

        assertEquals(GameUiState.Idle, vm.state.value)
        assertEquals(7, passport.stamps.value.size)
    }

    @Test
    fun `results of an abandoned game never replace a newer game`() {
        val gated = GatedPassport(passport)
        val vm = viewModel(gated)
        vm.startNewGame()
        vm.winUntilLastRoundGuessed()
        vm.continueGame()

        vm.exitToMenu()
        vm.startNewGame()
        gated.release()

        assertEquals(1, vm.playing.game.roundNumber)
        assertTrue(vm.playing.stamps.isEmpty())
    }
}
