package me.vladcip.guessthecountry.testing

import me.vladcip.guessthecountry.domain.model.Country

/** Ids are valid ISO codes ("AA", "AB", …) so flags can be built from them. */
fun testCountry(n: Int) = Country(
    id = "${'A' + (n / 26)}${'A' + (n % 26)}",
    name = "Country $n",
    capital = "Capital $n",
    hints = List(4) { "Hint ${it + 1} for country $n" },
)

fun testCountries(count: Int) = List(count) { testCountry(it + 1) }
