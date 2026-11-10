# Passport v2 — Plan B: Domain & Data Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Everything Passport v2 needs below the UI: country regions, stamp tiers, the passport collection (best-ever tier per country), milestones (first visit, daily postmark), the chosen passport cover, and game/home/passport view-model state that the Plan C/D screens will render.

**Architecture:** Pure domain types in `domain/passport`; one Room-backed `PassportRepository` (three new tables, DB stays version 1); `GameViewModel` records stamps the moment a hint round is won and the postmark when the last free game starts, and hands the results screen a `GameSummary`; a new `PassportViewModel` serves the collection and cover choice; `HomeViewModel` gains stamp counts.

**Tech Stack:** Kotlin Multiplatform, Room KMP 2.8, kotlinx-datetime, Koin, kotlin-test + Turbine + coroutines-test.

**Spec:** `docs/superpowers/specs/2026-10-23-passport-v2-design.md` (Part 4: Rounds, Results, My passport, Milestone stamps, When things are saved, Data changes).

**Depends on:** `domain/passport/PassportCover.kt` from Plan A Task 3. If Plan B runs first, create that file here (same content: `enum class PassportCover { Burgundy, Navy, Green, Black }`) and Plan A skips it.

## Global Constraints

- Room `@Database(version = 1)` — change the schema in place, no migrations; regenerate `shared/schemas/.../1.json`; testers reinstall the app.
- Game rules are unchanged: 7 rounds, 6 options, hint scoring 5→2 then capital 1, flag 1 pt, 3 free games/day, ad credits.
- Only `HintRound`s that are **won** create collection stamps; `FlagRound`s never touch the collection.
- Collection keeps the **best-ever** tier per country; `firstStampedOn` never changes after the first stamp.
- A stamp is saved **when its round is won**, not at game end; quitting keeps it.
- Daily postmark: earned when the **3rd free game of the day starts** (not premium, not an ad-credit game), at most once per calendar day, even if that game is quit.
- No streak, no premium visa stamp, no region lookup for the passport picker.

## Review Focus

1. **Premium player** starts a game → no postmark ever; their won hint rounds still stamp. Test in Task 4.
2. **Ad-credit game** (4th+ game of the day) → no postmark, and no second postmark if one exists today. Test in Task 4.
3. **Re-stamping a country with a lower tier** later → best tier stays, `firstStampedOn` stays, outcome says `isNewBest = false, isFirstVisit = false`. Test in Task 3.
4. **Quit mid-game** after winning round 1 → the stamp is in the repository and `Finished` is never emitted; the next game's summary doesn't include the old stamp. Test in Task 4.
5. **Day rollover**: postmark on day 1, three more free games on day 2 → second postmark dated day 2. Test in Task 4.

---

## File Structure

```
shared/src/commonMain/kotlin/me/vladcip/guessthecountry/
  domain/model/Country.kt                 # + region
  domain/model/Region.kt                  # new
  domain/passport/PassportCover.kt        # (Plan A) or here
  domain/passport/StampTier.kt            # new
  domain/passport/Passport.kt             # CountryStamp, StampOutcome, PassportRepository
  domain/progress/PlayerProgress.kt       # + usesLastFreeGame
  data/db/Entities.kt                     # + region column, 3 new entities
  data/db/PassportDao.kt                  # new
  data/db/AppDatabase.kt                  # register entities + dao
  data/repository/RoomPassportRepository.kt  # new
  data/repository/RoomCountryRepository.kt   # map region
  data/seed/SeedCountries.kt, DatabaseSeeder.kt  # region
  presentation/GameViewModel.kt           # stamps, postmark, GameSummary
  presentation/GameSummary.kt             # new
  presentation/HomeViewModel.kt           # stamp counts
  presentation/PassportViewModel.kt       # new
  di/Koin.kt
  ui/App.kt                               # Finished(summary) call-site only
shared/src/commonTest/.../
  testing/Fakes.kt                        # FakePassportRepository
  domain/passport/StampTierTest.kt
  domain/progress/PlayerProgressTest.kt   # + usesLastFreeGame
  data/seed/SeedCountriesTest.kt          # + regions
  presentation/GameViewModelTest.kt       # + passport behaviour
  presentation/HomeViewModelTest.kt       # + counts
  presentation/PassportViewModelTest.kt   # new
shared/src/iosTest/.../data/RoomRepositoriesTest.kt  # + RoomPassportRepository
```

---

### Task 1: Regions on countries

**Files:** Create `domain/model/Region.kt`. Modify `domain/model/Country.kt`, `data/db/Entities.kt` (`CountryEntity.region: String`), `data/seed/SeedCountries.kt`, `data/seed/DatabaseSeeder.kt`, `data/repository/RoomCountryRepository.kt`, `testing/TestCountries.kt`, `data/seed/SeedCountriesTest.kt`, regenerate `shared/schemas/…/1.json`.

**Interfaces:**
- Produces: `enum class Region(val label: String) { Europe("Europe"), Asia("Asia"), Africa("Africa"), Americas("Americas"), Oceania("Oceania") }` (declaration order = chip order); `Country(id, name, capital, hints, region: Region)`. Stored as `Region.name`.

- [ ] **Step 1: Add to `SeedCountriesTest`:**
  - `every region has at least one country`: `assertEquals(Region.entries.toSet(), countries.map { it.region }.toSet())`.
  - `regions match geography`: `mapOf("JP" to Asia, "BR" to Americas, "CA" to Americas, "AU" to Oceania, "EG" to Africa, "FR" to Europe, "IT" to Europe, "IN" to Asia, "MX" to Americas, "KE" to Africa, "AR" to Americas, "NO" to Europe, "PE" to Americas, "ZA" to Africa, "TH" to Asia, "MA" to Africa, "GR" to Europe, "NZ" to Oceania, "VN" to Asia, "IS" to Europe, "DE" to Europe)` equals `countries.associate { it.id to it.region }`.
- [ ] **Step 2: Run** `./gradlew :shared:testAndroidHostTest --tests '*SeedCountriesTest*'` → FAIL (no `region`).
- [ ] **Step 3: Implement**: add `region` to `Country` (required, no default), seed values per the map, entity column, seeder + repository mapping, `testCountries(n)` assigns regions round-robin over `Region.entries`.
- [ ] **Step 4: Run** `./gradlew :shared:testAndroidHostTest` → PASS; `./gradlew :shared:iosSimulatorArm64Test` → PASS (Room round-trip test `seedsTheDatabaseOnFirstUseWithHintsInOrder` now covers region).
- [ ] **Step 5: Commit** — "Add regions to countries".

---

### Task 2: Stamp tiers

**Files:** Create `domain/passport/StampTier.kt`, `commonTest/.../domain/passport/StampTierTest.kt`.

**Interfaces:**
- Produces: `enum class StampTier(val points: Int) { FirstClue(5), Admitted(4), Entry(3), Transit(2), Capital(1); companion object { fun forPoints(points: Int): StampTier? } }` — `null` for 0 (missed). (`Transit` = the purple octagon; name is internal only.)

- [ ] **Step 1: Write tests:** `maps every scoring outcome`: 5→FirstClue, 4→Admitted, 3→Entry, 2→Transit, 1→Capital, 0→null; `rejects impossible points`: `assertFailsWith<IllegalArgumentException> { StampTier.forPoints(6) }` and for −1; `better tier compares higher`: `StampTier.FirstClue.points > StampTier.Capital.points`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** — "Add stamp tiers".

---

### Task 3: Passport repository (collection, postmarks, cover)

**Files:** Create `domain/passport/Passport.kt`, `data/db/PassportDao.kt`, `data/repository/RoomPassportRepository.kt`. Modify `data/db/Entities.kt`, `data/db/AppDatabase.kt`, `di/Koin.kt`, `testing/Fakes.kt`, `iosTest/.../data/RoomRepositoriesTest.kt`, regenerate schema JSON.

**Interfaces:**
- Produces (domain, package `domain.passport`):
  - `data class CountryStamp(val countryId: String, val bestTier: StampTier, val firstStampedOn: LocalDate)`
  - `data class StampOutcome(val tier: StampTier, val isNewBest: Boolean, val isFirstVisit: Boolean)`
  - `interface PassportRepository {`
    `val stamps: Flow<List<CountryStamp>>`
    `val postmarks: Flow<List<LocalDate>>` (ascending)
    `val cover: Flow<PassportCover?>` (null until chosen)
    `suspend fun recordStamp(countryId: String, tier: StampTier, day: LocalDate): StampOutcome`
    `suspend fun recordPostmark(day: LocalDate): Boolean` (false if that day already has one)
    `suspend fun setCover(cover: PassportCover)` `}`
- Room (data): `country_stamps(countryId TEXT PK → countries.id CASCADE, bestPoints INT, firstStampedEpochDay LONG)`, `postmarks(epochDay LONG PK)`, `passport(id INT PK = 0, cover TEXT)`. `PassportDao` with `observeStamps()`, `getStamp(countryId)`, `upsertStamp`, `observePostmarks()`, `insertPostmark` (`OnConflictStrategy.IGNORE`, returns rowId; −1 = existed), `observeCover()`, `upsertPassport`, and a `@Transaction suspend fun recordStamp(…)` doing read-compare-write.
- `FakePassportRepository` in `testing/Fakes.kt` implementing the same rules in memory (MutableStateFlows), used by Task 4–5 tests.

- [ ] **Step 1: Write Room tests** in `RoomRepositoriesTest` (seed first, real in-memory DB):
  - `firstStampIsAFirstVisitAndNewBest`: `recordStamp("JP", Admitted, today)` → `StampOutcome(Admitted, isNewBest = true, isFirstVisit = true)`; `stamps.first()` == `[CountryStamp("JP", Admitted, today)]`.
  - `betterTierUpgradesButKeepsFirstDate`: then `recordStamp("JP", FirstClue, today + 3 days)` → `(FirstClue, true, false)`; stored `bestTier == FirstClue`, `firstStampedOn == today`.
  - `lowerTierNeverDowngrades`: then `recordStamp("JP", Capital, …)` → `(Capital, false, false)`; stored still `FirstClue`.
  - `postmarkOncePerDay`: `recordPostmark(today)` true, again false, `recordPostmark(today + 1)` true; `postmarks.first() == [today, today + 1]`.
  - `coverIsNullUntilChosen`: `cover.first() == null`; after `setCover(Green)` → `Green`.
- [ ] **Step 2: Run** `./gradlew :shared:iosSimulatorArm64Test` → FAIL.
- [ ] **Step 3: Implement** entities, DAO, repository, fake, Koin `single { get<AppDatabase>().passportDao() }` and `single<PassportRepository> { RoomPassportRepository(get()) }`.
- [ ] **Step 4: Run** `./gradlew :shared:iosSimulatorArm64Test :shared:testAndroidHostTest` → PASS.
- [ ] **Step 5: Commit** — "Add passport repository for stamps, postmarks and cover".

---

### Task 4: Game view model — stamps, postmark, game summary

**Files:** Create `presentation/GameSummary.kt`. Modify `domain/progress/PlayerProgress.kt`, `presentation/GameViewModel.kt`, `ui/App.kt` (only the `Finished` call site: `GameOverScreen(score = state.summary.score, maxScore = state.summary.maxScore, …)`), `di/Koin.kt`, `commonTest/.../domain/progress/PlayerProgressTest.kt`, `commonTest/.../presentation/GameViewModelTest.kt`.

**Interfaces:**
- Consumes: `PassportRepository`, `StampTier.forPoints`, `TodayProvider`.
- Produces:
  - `PlayerProgress.usesLastFreeGame(today: LocalDate): Boolean` = `!isPremium && freeGamesLeft(today) == 1`.
  - `data class RoundSummary(val number: Int, val country: Country, val points: Int, val isWon: Boolean, val stamp: StampOutcome?)` — `stamp` non-null only for won hint rounds.
  - `data class GameSummary(val mode: GameMode, val score: Int, val maxScore: Int, val playedOn: LocalDate, val rounds: List<RoundSummary>, val firstVisits: List<Country>, val postmark: LocalDate?)` — `firstVisits` in round order; `postmark` non-null only if this game earned it.
  - `GameUiState.Playing(val game: Game, val stamps: Map<Int, StampOutcome> = emptyMap(), val playedOn: LocalDate)` — key = round index.
  - `GameUiState.Finished(val summary: GameSummary)` (replaces `mode/score/maxScore`).
  - `GameViewModel(countryRepository, progressRepository, passportRepository: PassportRepository, gameFactory, todayProvider, adService)`.

- [ ] **Step 1: Write tests.** `PlayerProgressTest`: `last free game is the third of the day` (fresh → false; after 2 `onGameStarted(today)` → true; after 3 → false); `premium never uses the last free game`.
  `GameViewModelTest` (add `private val passport = FakePassportRepository()`):
  - `winning a hint round stamps the country immediately`: start, `guess(correct)` on round 1 (5 pts) → `passport.stamps.value == [CountryStamp(id, FirstClue, today)]` and `playing.stamps[0] == StampOutcome(FirstClue, true, true)`, before `continueGame()`.
  - `losing a hint round records nothing`: eliminate down to capital, guess wrong → `passport.stamps.value` empty, `playing.stamps` empty.
  - `flag rounds never stamp`: `startNewGame(GuessTheFlag)`, play whole game correctly → stamps empty; summary rounds all have `stamp == null`.
  - `quitting keeps earned stamps`: win round 1, `exitToMenu()` → stamp still in `passport.stamps.value`; start a new game, finish it → its summary `rounds` only contain the new game's 7 rounds.
  - `summary lists rounds, first visits and score`: play a whole hint game correctly → `Finished.summary.rounds.size == 7`, `firstVisits.size == 7` in round order, `score == 35`, `maxScore == 35`, `playedOn == today`, `postmark == null` (first game of the day).
  - `third free game earns the postmark even if quit`: `useFreeGames()` (helper starts+quits 3 games) → `passport.postmarks.value == [today]`.
  - `postmark shows on the summary of the game that earned it`: start+quit twice, then play the 3rd game to the end → `summary.postmark == today`.
  - `ad game earns no postmark`: after 3 free games, `watchAd` → `finishAd(Rewarded)` → still exactly one postmark.
  - `premium earns no postmark`: progress initial `PlayerProgress(isPremium = true)`, start 3 games → postmarks empty; a won hint round still stamps.
  - `postmark again on a new day`: 3 games, `today.advanceOneDay()`, 3 games → `postmarks == [day1, day2]`.
- [ ] **Step 2: Run** `./gradlew :shared:testAndroidHostTest --tests '*GameViewModelTest*' --tests '*PlayerProgressTest*'` → FAIL.
- [ ] **Step 3: Implement.** In `start(mode)`: read progress once; `val earnsPostmark = progress.usesLastFreeGame(today)`; after `recordGameStarted(today)`, if `earnsPostmark && passportRepository.recordPostmark(today)` remember `today` for this game. In `guess(option)`: after the round update, if the current round is a `HintRound` that just became `isWon` and its index isn't in `stamps`, launch `passportRepository.recordStamp(country.id, StampTier.forPoints(pointsEarned)!!, playedOn)` and put the outcome into `Playing.stamps`. In `continueGame()` when `game.isOver`, build `GameSummary` from `Playing` (rounds, `stamps`, first visits = outcomes with `isFirstVisit`, remembered postmark). Reset per-game state in `start` and `exitToMenu`. Update Koin (`viewModelOf(::GameViewModel)` picks up the new parameter) and the existing test's `viewModel()` helper.
- [ ] **Step 4: Run** `./gradlew :shared:testAndroidHostTest` → PASS (all, including existing GameViewModel tests); `./gradlew :androidApp:assembleDebug` → SUCCESS.
- [ ] **Step 5: Commit** — "Record passport stamps and daily postmark during games".

---

### Task 5: Passport and home view-model state

**Files:** Create `presentation/PassportViewModel.kt`, `commonTest/.../presentation/PassportViewModelTest.kt`. Modify `presentation/HomeViewModel.kt`, `commonTest/.../presentation/HomeViewModelTest.kt`, `di/Koin.kt`.

**Interfaces:**
- Consumes: `CountryRepository`, `PassportRepository`.
- Produces:
  - `data class CollectionTileState(val country: Country, val bestTier: StampTier?)`
  - `data class Moments(val firstVisits: Int, val lastFirstVisit: LocalDate?, val postmarks: Int, val lastPostmark: LocalDate?)`
  - `data class PassportUiState(val isLoading: Boolean = true, val cover: PassportCover? = null, val needsCoverChoice: Boolean = false, val stampedCount: Int = 0, val totalCountries: Int = 0, val selectedRegion: Region? = null, val tiles: List<CollectionTileState> = emptyList(), val moments: Moments = Moments(0, null, 0, null))` — `selectedRegion == null` = "All"; `tiles` filtered by it and sorted by country name; `stampedCount`/`totalCountries` are **not** filtered; `needsCoverChoice = !isLoading && cover == null`.
  - `class PassportViewModel(countryRepository, passportRepository) : ViewModel { val state: StateFlow<PassportUiState>; fun selectRegion(region: Region?); fun chooseCover(cover: PassportCover) }`
  - `HomeUiState` gains `stampedCount: Int = 0`, `totalCountries: Int = 0`, `freeGamesUsedToday: Int = 0`, `today: LocalDate? = null` (for the "USED dd.MM" slots). `HomeViewModel(progressRepository, passportRepository, countryRepository, todayProvider)`.

- [ ] **Step 1: Write tests** (`PassportViewModelTest`, Turbine on `state`):
  - `needs a cover choice until one is set`: fresh → after loading `needsCoverChoice == true`; `chooseCover(Navy)` → `cover == Navy`, `needsCoverChoice == false`.
  - `counts and tiles`: fake has stamps for 2 of 21 countries → `stampedCount == 2`, `totalCountries == 21`, 21 tiles, 2 with `bestTier != null`.
  - `region filter narrows tiles but not counts`: `selectRegion(Oceania)` → tiles only Oceania countries; `stampedCount` unchanged; `selectRegion(null)` → 21 tiles.
  - `moments summarise milestones`: stamps first-stamped on d1 and d3, postmarks d2 and d4 → `Moments(2, d3, 2, d4)`.
  `HomeViewModelTest`: `exposes stamp counts and today's used entries` — 3 stamps, 2 free games used → `stampedCount == 3`, `totalCountries == 21`, `freeGamesUsedToday == 2`, `today == FakeToday().date`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** + Koin `viewModelOf(::PassportViewModel)`. **Step 4: Run** `./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test` → PASS.
- [ ] **Step 5: Commit** — "Add passport collection and home stamp state".
