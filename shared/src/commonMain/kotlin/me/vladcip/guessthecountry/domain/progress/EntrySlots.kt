package me.vladcip.guessthecountry.domain.progress

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/** One of today's free games on the main menu. */
data class EntrySlotState(
    /** 1 to [PlayerProgress.FREE_GAMES_PER_DAY], left to right. */
    val number: Int,
    /** Today's date as "dd.MM" once this game is used, `null` while it's still free. */
    val usedOn: String?,
    /** What screen readers announce, e.g. "Free game 1, used today". */
    val description: String,
)

/**
 * Today's [PlayerProgress.FREE_GAMES_PER_DAY] free games as slots. The first [usedToday] are used, so
 * slots always fill left to right.
 */
fun entrySlotsOf(usedToday: Int, today: LocalDate): List<EntrySlotState> {
    val used = usedToday.coerceIn(0, PlayerProgress.FREE_GAMES_PER_DAY)
    val date = "${today.day.twoDigits()}.${today.month.number.twoDigits()}"
    return (1..PlayerProgress.FREE_GAMES_PER_DAY).map { number ->
        val isUsed = number <= used
        EntrySlotState(
            number = number,
            usedOn = if (isUsed) date else null,
            description = "Free game $number, ${if (isUsed) "used today" else "available"}",
        )
    }
}

private fun Int.twoDigits() = toString().padStart(2, '0')
