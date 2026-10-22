package me.vladcip.guessthecountry.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.vladcip.guessthecountry.presentation.GameUiState
import me.vladcip.guessthecountry.presentation.GameViewModel
import me.vladcip.guessthecountry.presentation.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Screens are driven by [GameUiState]; no navigation library yet.
 * Every screen except the main menu has a "Main menu" button, and system back does the same.
 */
@Composable
fun App(
    gameViewModel: GameViewModel = koinViewModel(),
    homeViewModel: HomeViewModel = koinViewModel(),
) {
    val gameState by gameViewModel.state.collectAsStateWithLifecycle()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(gameState) {
        if (gameState == GameUiState.Idle) homeViewModel.refresh()
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                when (val state = gameState) {
                    GameUiState.Idle -> MainMenuScreen(
                        state = homeState,
                        onPlay = gameViewModel::startNewGame,
                        onWatchAd = gameViewModel::watchAd,
                        onUpgrade = homeViewModel::onUpgradeClicked,
                    )

                    GameUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                    is GameUiState.ShowingAd -> AdPlaceholderScreen(
                        onAdFinished = gameViewModel::onAdWatched,
                        onMainMenu = gameViewModel::exitToMenu,
                    )

                    is GameUiState.Playing -> GameScreen(
                        game = state.game,
                        onGuess = gameViewModel::guess,
                        onNextClue = gameViewModel::nextClue,
                        onSkipToCapital = gameViewModel::skipToCapital,
                        onContinue = gameViewModel::continueGame,
                        onQuit = gameViewModel::exitToMenu,
                    )

                    is GameUiState.Finished -> GameOverScreen(
                        score = state.score,
                        maxScore = state.maxScore,
                        onMainMenu = gameViewModel::exitToMenu,
                    )

                    is GameUiState.Error -> ErrorScreen(
                        message = state.message,
                        onRetry = { gameViewModel.startNewGame(state.mode) },
                        onMainMenu = gameViewModel::exitToMenu,
                    )
                }
            }
        }
    }
}
