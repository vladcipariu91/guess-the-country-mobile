package me.vladcip.guessthecountry.data.repository

import me.vladcip.guessthecountry.data.db.CountryStampEntity
import me.vladcip.guessthecountry.data.db.PassportDao
import me.vladcip.guessthecountry.data.db.PassportEntity
import me.vladcip.guessthecountry.data.db.PostmarkEntity
import me.vladcip.guessthecountry.domain.passport.CountryStamp
import me.vladcip.guessthecountry.domain.passport.PassportCover
import me.vladcip.guessthecountry.domain.passport.PassportRepository
import me.vladcip.guessthecountry.domain.passport.StampOutcome
import me.vladcip.guessthecountry.domain.passport.StampTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class RoomPassportRepository(
    private val dao: PassportDao,
) : PassportRepository {
    override val stamps: Flow<List<CountryStamp>> =
        dao.observeStamps().map { rows -> rows.mapNotNull { it.toDomain() } }

    override val postmarks: Flow<List<LocalDate>> =
        dao.observePostmarks().map { rows -> rows.map { LocalDate.fromEpochDays(it.epochDay) } }

    override val cover: Flow<PassportCover?> =
        dao.observeCover().map { row -> PassportCover.entries.firstOrNull { it.name == row?.cover } }

    override suspend fun recordStamp(countryId: String, tier: StampTier, day: LocalDate): StampOutcome {
        val write = dao.recordStamp(countryId, tier.points, day.toEpochDays())
        return StampOutcome(tier, isNewBest = write.isNewBest, isFirstVisit = write.isFirstVisit)
    }

    override suspend fun recordPostmark(day: LocalDate): Boolean =
        dao.insertPostmark(PostmarkEntity(day.toEpochDays())) != -1L

    override suspend fun setCover(cover: PassportCover) =
        dao.upsertPassport(PassportEntity(cover = cover.name))
}

private fun CountryStampEntity.toDomain(): CountryStamp? {
    val tier = StampTier.entries.firstOrNull { it.points == bestPoints } ?: return null
    return CountryStamp(countryId, tier, LocalDate.fromEpochDays(firstStampedEpochDay))
}
