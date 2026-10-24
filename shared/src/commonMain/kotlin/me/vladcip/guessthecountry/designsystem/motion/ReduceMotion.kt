package me.vladcip.guessthecountry.designsystem.motion

import androidx.compose.runtime.Composable

/**
 * Whether the OS asks apps to minimise motion. Android has no dedicated flag, so a zero animator
 * duration scale stands in for it; iOS exposes the Reduce Motion accessibility setting.
 */
@Composable
expect fun rememberReduceMotion(): Boolean
