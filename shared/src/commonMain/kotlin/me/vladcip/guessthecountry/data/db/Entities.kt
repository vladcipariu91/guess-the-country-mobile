package me.vladcip.guessthecountry.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "countries")
data class CountryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val capital: String,
    /** [me.vladcip.guessthecountry.domain.model.Region.name]. */
    val region: String,
)

@Entity(
    tableName = "hints",
    foreignKeys = [
        ForeignKey(
            entity = CountryEntity::class,
            parentColumns = ["id"],
            childColumns = ["countryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["countryId", "position"], unique = true)],
)
data class HintEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val countryId: String,
    /** 0 = hardest hint. */
    val position: Int,
    val text: String,
)

data class CountryWithHints(
    @Embedded val country: CountryEntity,
    @Relation(parentColumn = "id", entityColumn = "countryId")
    val hints: List<HintEntity>,
)

@Entity(tableName = "player_progress")
data class PlayerProgressEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val gamesPlayed: Int,
    /** Epoch day that [freeGamesUsed] refers to. */
    val freeGamesEpochDay: Long?,
    val freeGamesUsed: Int,
    val adCredits: Int,
    val isPremium: Boolean,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

@Entity(
    tableName = "country_stamps",
    foreignKeys = [
        ForeignKey(
            entity = CountryEntity::class,
            parentColumns = ["id"],
            childColumns = ["countryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CountryStampEntity(
    @PrimaryKey val countryId: String,
    /** [me.vladcip.guessthecountry.domain.passport.StampTier.points] of the best stamp. */
    val bestPoints: Int,
    val firstStampedEpochDay: Long,
)

@Entity(tableName = "postmarks")
data class PostmarkEntity(
    @PrimaryKey val epochDay: Long,
)

@Entity(tableName = "passport")
data class PassportEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    /** [me.vladcip.guessthecountry.domain.passport.PassportCover.name]; null until chosen. */
    val cover: String?,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
