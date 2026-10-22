package me.vladcip.guessthecountry.domain.model

private const val REGIONAL_INDICATOR_A = 0x1F1E6

/** The country's flag as an emoji, built from its ISO 3166-1 alpha-2 [Country.id] (e.g. "JP" → 🇯🇵). */
val Country.flag: String get() = flagEmoji(id)

fun flagEmoji(isoCode: String): String {
    require(isoCode.length == 2 && isoCode.all { it in 'A'..'Z' }) { "Not an ISO alpha-2 code: $isoCode" }
    return buildString {
        isoCode.forEach { letter -> appendCodePoint(REGIONAL_INDICATOR_A + (letter - 'A')) }
    }
}

// Regional indicators are outside the BMP, so each one is a surrogate pair.
private fun StringBuilder.appendCodePoint(codePoint: Int) {
    val offset = codePoint - 0x10000
    append(Char(0xD800 + (offset shr 10)))
    append(Char(0xDC00 + (offset and 0x3FF)))
}
