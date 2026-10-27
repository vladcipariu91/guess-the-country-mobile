package me.vladcip.guessthecountry.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.vladcip.guessthecountry.domain.model.Country
import me.vladcip.guessthecountry.domain.model.CountryRepository
import me.vladcip.guessthecountry.domain.model.Region
import me.vladcip.guessthecountry.domain.passport.CountryStamp
import me.vladcip.guessthecountry.domain.passport.PassportCover
import me.vladcip.guessthecountry.domain.passport.PassportRepository
import me.vladcip.guessthecountry.domain.passport.StampTier

/** One country in the "My passport" collection; [bestTier] is `null` until it has been stamped. */
data class CollectionTileState(val country: Country, val bestTier: StampTier?)

/** First visits (countries stamped) and daily postmarks, each with the day of the latest one. */
data class Moments(
    val firstVisits: Int,
    val lastFirstVisit: LocalDate?,
    val postmarks: Int,
    val lastPostmark: LocalDate?,
)

data class PassportUiState(
    val isLoading: Boolean = true,
    val cover: PassportCover? = null,
    /** True when the player hasn't picked a passport yet: show the cover picker. */
    val needsCoverChoice: Boolean = false,
    /** Always unfiltered: the "N of 21 stamped" counts ignore [selectedRegion]. */
    val stampedCount: Int = 0,
    val totalCountries: Int = 0,
    /** `null` means "All". */
    val selectedRegion: Region? = null,
    /** Filtered by [selectedRegion] and sorted by country name. */
    val tiles: List<CollectionTileState> = emptyList(),
    val moments: Moments = Moments(0, null, 0, null),
)

class PassportViewModel(
    countryRepository: CountryRepository,
    private val passportRepository: PassportRepository,
) : ViewModel() {
    private val selectedRegion = MutableStateFlow<Region?>(null)

    // If the countries can't be read, the passport shows empty rather than staying on the loader.
    private val countries = flow { emit(countryRepository.getAllCountries()) }
        .catch { emit(emptyList()) }

    val state: StateFlow<PassportUiState> = combine(
        countries,
        passportRepository.stamps,
        passportRepository.postmarks,
        passportRepository.cover,
        selectedRegion,
    ) { countries, stamps, postmarks, cover, region ->
        val tierByCountry = stamps.associate { it.countryId to it.bestTier }
        PassportUiState(
            isLoading = false,
            cover = cover,
            needsCoverChoice = cover == null,
            stampedCount = stamps.size,
            totalCountries = countries.size,
            selectedRegion = region,
            tiles = countries
                .filter { region == null || it.region == region }
                .sortedBy { it.name }
                .map { CollectionTileState(it, tierByCountry[it.id]) },
            moments = momentsOf(stamps, postmarks),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PassportUiState())

    fun selectRegion(region: Region?) {
        selectedRegion.value = region
    }

    fun chooseCover(cover: PassportCover) {
        viewModelScope.launch { passportRepository.setCover(cover) }
    }

    private fun momentsOf(stamps: List<CountryStamp>, postmarks: List<LocalDate>) = Moments(
        firstVisits = stamps.size,
        lastFirstVisit = stamps.maxOfOrNull { it.firstStampedOn },
        postmarks = postmarks.size,
        lastPostmark = postmarks.maxOrNull(),
    )
}
