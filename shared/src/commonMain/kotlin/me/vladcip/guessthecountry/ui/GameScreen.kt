package me.vladcip.guessthecountry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.vladcip.guessthecountry.domain.game.FlagRound
import me.vladcip.guessthecountry.domain.game.Game
import me.vladcip.guessthecountry.domain.game.GameRound
import me.vladcip.guessthecountry.domain.game.HintRound
import me.vladcip.guessthecountry.domain.game.RoundPhase
import me.vladcip.guessthecountry.domain.model.Country
import me.vladcip.guessthecountry.domain.model.flag

@Composable
fun GameScreen(
    game: Game,
    onGuess: (Country) -> Unit,
    onNextClue: () -> Unit,
    onSkipToCapital: () -> Unit,
    onContinue: () -> Unit,
    onQuit: () -> Unit,
) {
    var confirmQuit by remember { mutableStateOf(false) }
    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Quit this game?") },
            text = { Text("You'll go back to the main menu and this game's score is lost.") },
            confirmButton = { TextButton(onClick = onQuit) { Text("Quit") } },
            dismissButton = { TextButton(onClick = { confirmQuit = false }) { Text("Keep playing") } },
        )
    }
    ScreenWithMenuButton(
        onMainMenu = { confirmQuit = true },
        title = "Round ${game.roundNumber}/${game.totalRounds} · Score ${game.score}",
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val round = game.currentRound) {
                is HintRound -> HintRoundContent(round, onGuess, onNextClue, onSkipToCapital)
                is FlagRound -> FlagRoundContent(round, onGuess)
            }
            if (game.currentRound.isFinished) {
                RoundResult(game.currentRound, isLastRound = game.isLastRound, onContinue = onContinue)
            }
        }
    }
}

@Composable
private fun ColumnScope.HintRoundContent(
    round: HintRound,
    onGuess: (Country) -> Unit,
    onNextClue: () -> Unit,
    onSkipToCapital: () -> Unit,
) {
    CluesCard(round)
    if (round.isFinished) return
    Text("Worth ${round.pointsAvailable} point${if (round.pointsAvailable == 1) "" else "s"}")
    AnswerOptions(round, showFlags = true, onGuess = onGuess)
    if (round.phase == RoundPhase.Hints) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNextClue, modifier = Modifier.weight(1f)) { Text("Another clue (-1)") }
            Button(onClick = onSkipToCapital, modifier = Modifier.weight(1f)) { Text("Show capital (1 pt)") }
        }
    } else {
        Text("Last chance: a wrong answer scores 0.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FlagRoundContent(round: FlagRound, onGuess: (Country) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Which country's flag is this?", style = MaterialTheme.typography.titleLarge)
            Text(round.country.flag, fontSize = 120.sp, textAlign = TextAlign.Center)
        }
    }
    if (round.isFinished) return
    Text("Worth ${FlagRound.POINTS} point · one guess")
    // No flags on the answers here: they would give it away.
    AnswerOptions(round, showFlags = false, onGuess = onGuess)
}

@Composable
private fun AnswerOptions(round: GameRound, showFlags: Boolean, onGuess: (Country) -> Unit) {
    round.options.forEach { option ->
        OutlinedButton(
            onClick = { onGuess(option) },
            enabled = round.isSelectable(option),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (showFlags) "${option.flag}  ${option.name}" else option.name) }
    }
}

@Composable
private fun CluesCard(round: HintRound) = Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Which country is this?", style = MaterialTheme.typography.titleLarge)
        round.revealedHints.forEachIndexed { index, hint ->
            Text("${index + 1}. $hint")
        }
        if (round.phase != RoundPhase.Hints) {
            Text("Its capital is ${round.country.capital}.", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoundResult(round: GameRound, isLastRound: Boolean, onContinue: () -> Unit) {
    val message = if (round.isWon) {
        "Correct! +${round.pointsEarned}"
    } else {
        "Not quite. It was ${round.country.flag} ${round.country.name}."
    }
    Text(message, style = MaterialTheme.typography.headlineSmall)
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
        Text(if (isLastRound) "See results" else "Next country")
    }
}
