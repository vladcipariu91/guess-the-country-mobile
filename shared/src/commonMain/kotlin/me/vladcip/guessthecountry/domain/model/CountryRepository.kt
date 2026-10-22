package me.vladcip.guessthecountry.domain.model

interface CountryRepository {
    suspend fun getAllCountries(): List<Country>
}
