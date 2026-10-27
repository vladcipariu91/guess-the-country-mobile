package me.vladcip.guessthecountry.domain.model

/** Where a country is. Declaration order is the order of the region filter chips. */
enum class Region(val label: String) {
    Europe("Europe"),
    Asia("Asia"),
    Africa("Africa"),
    Americas("Americas"),
    Oceania("Oceania"),
}
