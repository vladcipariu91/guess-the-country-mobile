package me.vladcip.guessthecountry.domain.model

import kotlinx.datetime.LocalDate

/**
 * The main menu's country for [date], or `null` when there are no [countries].
 *
 * Countries are sorted by [Country.id] so the pick doesn't depend on storage order, and two devices with
 * the same content agree. Each day steps to the next country, so consecutive days differ (with 2 or more)
 * and the whole list comes up before any repeat. `mod` keeps dates before 1970 non-negative.
 */
fun countryOfTheDay(countries: List<Country>, date: LocalDate): Country? {
    if (countries.isEmpty()) return null
    val sorted = countries.sortedBy { it.id }
    return sorted[date.toEpochDays().mod(sorted.size)]
}
