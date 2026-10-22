package me.vladcip.guessthecountry.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerProgressDao {
    @Query("SELECT * FROM player_progress WHERE id = ${PlayerProgressEntity.SINGLETON_ID}")
    fun observe(): Flow<PlayerProgressEntity?>

    @Query("SELECT * FROM player_progress WHERE id = ${PlayerProgressEntity.SINGLETON_ID}")
    suspend fun get(): PlayerProgressEntity?

    @Upsert
    suspend fun upsert(progress: PlayerProgressEntity)

    @Transaction
    suspend fun update(transform: (PlayerProgressEntity?) -> PlayerProgressEntity) {
        upsert(transform(get()))
    }
}
