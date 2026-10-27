package me.vladcip.guessthecountry.data.seed

import me.vladcip.guessthecountry.data.db.CountryDao
import me.vladcip.guessthecountry.data.db.CountryEntity
import me.vladcip.guessthecountry.data.db.HintEntity
import me.vladcip.guessthecountry.domain.model.Country
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Pre-populates the question database the first time it's used. */
class DatabaseSeeder(
    private val countryDao: CountryDao,
    private val countries: List<Country> = SeedCountries.all,
) {
    private val mutex = Mutex()
    private var seeded = false

    suspend fun seedIfEmpty() = mutex.withLock {
        if (seeded) return@withLock
        if (countryDao.count() == 0) {
            countryDao.insertAll(
                countries = countries.map { CountryEntity(it.id, it.name, it.capital, it.region.name) },
                hints = countries.flatMap { country ->
                    country.hints.mapIndexed { index, text ->
                        HintEntity(countryId = country.id, position = index, text = text)
                    }
                },
            )
        }
        seeded = true
    }
}
