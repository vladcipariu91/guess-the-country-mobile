package me.vladcip.guessthecountry.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CountryDao {
    @Transaction
    @Query("SELECT * FROM countries")
    suspend fun getAllWithHints(): List<CountryWithHints>

    @Query("SELECT COUNT(*) FROM countries")
    suspend fun count(): Int

    @Insert
    suspend fun insertCountries(countries: List<CountryEntity>)

    @Insert
    suspend fun insertHints(hints: List<HintEntity>)

    @Transaction
    suspend fun insertAll(countries: List<CountryEntity>, hints: List<HintEntity>) {
        insertCountries(countries)
        insertHints(hints)
    }
}
