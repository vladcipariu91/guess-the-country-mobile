package me.vladcip.guessthecountry.data.repository

import me.vladcip.guessthecountry.data.db.CountryDao
import me.vladcip.guessthecountry.data.db.CountryWithHints
import me.vladcip.guessthecountry.data.seed.DatabaseSeeder
import me.vladcip.guessthecountry.domain.model.Country
import me.vladcip.guessthecountry.domain.model.CountryRepository

class RoomCountryRepository(
    private val countryDao: CountryDao,
    private val seeder: DatabaseSeeder,
) : CountryRepository {
    override suspend fun getAllCountries(): List<Country> {
        seeder.seedIfEmpty()
        return countryDao.getAllWithHints().map { it.toDomain() }
    }
}

internal fun CountryWithHints.toDomain() = Country(
    id = country.id,
    name = country.name,
    capital = country.capital,
    hints = hints.sortedBy { it.position }.map { it.text },
)
