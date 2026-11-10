package me.vladcip.guessthecountry.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** What [PassportDao.recordStamp] changed. */
data class StampWrite(val isNewBest: Boolean, val isFirstVisit: Boolean)

@Dao
interface PassportDao {
    @Query("SELECT * FROM country_stamps ORDER BY countryId")
    fun observeStamps(): Flow<List<CountryStampEntity>>

    @Query("SELECT * FROM country_stamps WHERE countryId = :countryId")
    suspend fun getStamp(countryId: String): CountryStampEntity?

    @Upsert
    suspend fun upsertStamp(stamp: CountryStampEntity)

    @Query("SELECT * FROM postmarks ORDER BY epochDay")
    fun observePostmarks(): Flow<List<PostmarkEntity>>

    /** Returns the new row id, or -1 when that day already has a postmark. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPostmark(postmark: PostmarkEntity): Long

    @Query("SELECT * FROM passport WHERE id = ${PassportEntity.SINGLETON_ID}")
    fun observeCover(): Flow<PassportEntity?>

    @Upsert
    suspend fun upsertPassport(passport: PassportEntity)

    /** Keeps the highest [points] and the original first-stamped day. */
    @Transaction
    suspend fun recordStamp(countryId: String, points: Int, epochDay: Long): StampWrite {
        val existing = getStamp(countryId)
        return when {
            existing == null -> {
                upsertStamp(CountryStampEntity(countryId, points, epochDay))
                StampWrite(isNewBest = true, isFirstVisit = true)
            }
            points > existing.bestPoints -> {
                upsertStamp(existing.copy(bestPoints = points))
                StampWrite(isNewBest = true, isFirstVisit = false)
            }
            else -> StampWrite(isNewBest = false, isFirstVisit = false)
        }
    }
}
