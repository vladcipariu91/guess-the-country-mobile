package me.vladcip.guessthecountry.designsystem.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import me.vladcip.guessthecountry.designsystem.tokens.Motion

/** Which haptic accompanies the stamp's impact: a crisp confirm when admitted, a dull reject when missed. */
enum class StampHaptic(internal val type: HapticFeedbackType) {
    Admitted(HapticFeedbackType.Confirm),
    Missed(HapticFeedbackType.Reject),
}

/** Remembers which stamps have already slammed so each plays once, never on recomposition or scroll-back. */
class StampSlamState(alreadyShown: Set<String> = emptySet()) {
    private val shown = alreadyShown.toMutableSet()

    val playedIds: Set<String> get() = shown.toSet()

    fun shouldPlay(stampId: String): Boolean = stampId !in shown

    fun markPlayed(stampId: String) {
        shown += stampId
    }
}

@Composable
fun rememberStampSlamState(): StampSlamState = rememberSaveable(
    saver = listSaver(
        save = { it.playedIds.toList() },
        restore = { StampSlamState(it.toSet()) },
    ),
) { StampSlamState() }

// Ease-out-back: lands on 1f with a slight overshoot, within exactly Motion.StampSlamMillis.
private val StampOvershootEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/**
 * Drops the stamp from [Motion.StampSlamStartScale] to 1x with a small overshoot, firing [haptic]
 * the moment it lands. Plays once per [stampId]. With reduce-motion on there is no scale animation
 * but the haptic still fires. Purely a draw-layer effect, so it never blocks input.
 */
fun Modifier.stampSlam(stampId: String, state: StampSlamState, haptic: StampHaptic): Modifier = composed {
    val reduceMotion = rememberReduceMotion()
    val hapticFeedback = LocalHapticFeedback.current
    val play = remember(stampId) { state.shouldPlay(stampId) }
    val scale = remember(stampId) {
        Animatable(if (play && !reduceMotion) Motion.StampSlamStartScale else 1f)
    }

    LaunchedEffect(stampId) {
        if (!play) return@LaunchedEffect
        state.markPlayed(stampId)
        if (reduceMotion) {
            hapticFeedback.performHapticFeedback(haptic.type)
        } else {
            var landed = false
            scale.animateTo(1f, tween(Motion.StampSlamMillis, easing = StampOvershootEasing)) {
                if (!landed && value <= 1f) {
                    landed = true
                    hapticFeedback.performHapticFeedback(haptic.type)
                }
            }
        }
    }

    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
