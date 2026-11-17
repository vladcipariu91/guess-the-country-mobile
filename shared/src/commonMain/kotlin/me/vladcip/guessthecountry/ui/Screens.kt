package me.vladcip.guessthecountry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import me.vladcip.guessthecountry.designsystem.components.EntrySlot
import me.vladcip.guessthecountry.designsystem.theme.GtcTheme
import me.vladcip.guessthecountry.designsystem.tokens.Radius
import me.vladcip.guessthecountry.designsystem.tokens.Space
import me.vladcip.guessthecountry.domain.game.GameMode
import me.vladcip.guessthecountry.domain.passport.PassportCover
import me.vladcip.guessthecountry.domain.progress.EntrySlotState
import me.vladcip.guessthecountry.domain.progress.PlayerProgress
import me.vladcip.guessthecountry.presentation.HomeUiState

@Composable
fun MainMenuScreen(
    state: HomeUiState,
    onPlay: (GameMode) -> Unit,
    onWatchAd: (GameMode) -> Unit,
    onUpgrade: () -> Unit,
) = CenteredColumn {
    Text("Guess the Country", style = MaterialTheme.typography.headlineLarge)
    state.passportProgress?.let {
        Text(
            "${it.stamped} of ${it.total} countries (${it.percent}%)",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
    Text("7 countries per game. Pick a mode:", textAlign = TextAlign.Center)
    if (state.entrySlots.isNotEmpty()) EntrySlotRow(state.entrySlots)
    if (state.canPlay) {
        ModeButton(GameMode.GuessTheCountry, "Guess the country", onPlay)
        Text(
            "Hints from hard to easy: 5 points down to 1 for the capital.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        ModeButton(GameMode.GuessTheFlag, "Guess the flag", onPlay)
        Text("Name the flag: 1 point each.", style = MaterialTheme.typography.bodySmall)
        if (!state.isPremium && state.freeGamesLeft == 0) {
            Text("Ad game unlocked", style = MaterialTheme.typography.bodySmall)
        }
    } else {
        Text(
            "You've played your ${PlayerProgress.FREE_GAMES_PER_DAY} free games today.",
            textAlign = TextAlign.Center,
        )
        ModeButton(GameMode.GuessTheCountry, "Watch an ad to play Guess the country", onWatchAd)
        ModeButton(GameMode.GuessTheFlag, "Watch an ad to play Guess the flag", onWatchAd)
        OutlinedButton(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) { Text("Upgrade") }
    }
}

/**
 * Today's free games, used ones stamped from the left, on a strip of passport cover. [App] doesn't
 * provide [GtcTheme] yet, so the row provides it, plus the cover background its on-cover colours need.
 */
@Composable
private fun EntrySlotRow(slots: List<EntrySlotState>) = GtcTheme(cover = PassportCover.Burgundy) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GtcTheme.colors.cover, RoundedCornerShape(Radius.R12))
            .padding(Space.S12),
        horizontalArrangement = Arrangement.spacedBy(Space.S8),
    ) {
        slots.forEach { slot ->
            EntrySlot(usedOn = slot.usedOn, contentDescription = slot.description, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ModeButton(mode: GameMode, label: String, onClick: (GameMode) -> Unit) =
    Button(onClick = { onClick(mode) }, modifier = Modifier.fillMaxWidth()) { Text(label, textAlign = TextAlign.Center) }

/** Shown behind the full-screen ad while it loads and plays. */
@Composable
fun AdScreen(onMainMenu: () -> Unit) = ScreenWithMenuButton(onMainMenu) {
    CenteredColumn {
        CircularProgressIndicator()
        Text("Loading a short ad…", textAlign = TextAlign.Center)
        Text(
            "Watch it to the end to unlock a free game.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun GameOverScreen(score: Int, maxScore: Int, onMainMenu: () -> Unit) {
    SystemBackHandler(onMainMenu)
    CenteredColumn {
        Text("Game over", style = MaterialTheme.typography.headlineMedium)
        Text("$score / $maxScore points", style = MaterialTheme.typography.displaySmall)
        Button(onClick = onMainMenu, modifier = Modifier.fillMaxWidth()) { Text("Main menu") }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit, onMainMenu: () -> Unit) = ScreenWithMenuButton(onMainMenu) {
    CenteredColumn {
        Text("Something went wrong", style = MaterialTheme.typography.headlineSmall)
        Text(message, textAlign = TextAlign.Center)
        Button(onClick = onRetry) { Text("Try again") }
    }
}

/** A top bar with a "Main menu" button; Android's back button does the same. */
@Composable
internal fun ScreenWithMenuButton(
    onMainMenu: () -> Unit,
    title: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    SystemBackHandler(onMainMenu)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onMainMenu) { Text("‹ Main menu") }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
        }
        content()
    }
}

@Composable
internal fun SystemBackHandler(onBack: () -> Unit) = NavigationBackHandler(
    state = rememberNavigationEventState(NavigationEventInfo.None),
    onBackCompleted = onBack,
)

@Composable
internal fun CenteredColumn(content: @Composable ColumnScope.() -> Unit) = Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
    content = content,
)
