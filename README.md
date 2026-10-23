# Guess the Country — mobile app

Kotlin Multiplatform (Android + iOS) with Compose Multiplatform UI, Room (KMP), Koin, coroutines/flows and AndroidX ViewModels.

## Layout

- `shared/` — the game, in `me.vladcip.guessthecountry`:
  - `domain/` — game rules (`HintRound`, `FlagRound`, `Game`, `GameFactory`) and the daily free-game / ad-credit rules (`PlayerProgress`). Pure Kotlin.
  - `data/` — Room database, DAOs, repositories, and the seeded questions (`data/seed/SeedCountries.kt`).
  - `presentation/` — `GameViewModel`, `HomeViewModel`.
  - `ui/` — Compose screens (`App` is the entry point).
- `ads/` — rewarded ads (Google AdMob + consent) behind a small `AdService` interface, so Google's SDKs stay
  out of the game code. Android uses the SDK directly; on iOS the SDK is a Swift package and
  `iosApp/iosApp/GoogleRewardedAds.swift` implements the `NativeRewardedAds` bridge.
- `androidApp/` — Android application shell.
- `iosApp/` — Xcode project; a build phase runs Gradle to embed the `Shared` framework (which re-exports `ads`).

Future store-dependent features (in-app purchases, leaderboards) get their own modules the same way.

## Rules

Two modes, 7 countries per game, multiple choice (6 options), no repeated countries:

- **Guess the country**: hints run 5 → 4 → 3 → 2 points; a wrong guess or "another clue" costs 1 point.
  The capital is the last clue (1 point, one try, wrong = 0) and the player can jump to it at any time.
  Answer options show each country's flag.
- **Guess the flag**: a flag is shown; one guess, 1 point if right, 0 if wrong.

Flags are emoji built from each country's ISO 3166-1 alpha-2 code (the `id` column), so there are no image assets.

Free players get 3 games per calendar day (local time), shared across both modes. After that the main menu offers
"Watch an ad to play a free game" (one ad = one game, which starts right after the ad) and "Upgrade"
(does nothing yet). Quitting (with a confirmation) or finishing a game returns to the main menu.
Ads and payments are placeholders.

## Commands

```
./gradlew :shared:testAndroidHostTest      # common tests on the JVM
./gradlew :shared:iosSimulatorArm64Test    # common tests + Room tests on the iOS simulator
./gradlew :ads:iosSimulatorArm64Test       # iOS ads bridge tests
./gradlew :androidApp:assembleDebug
open iosApp/iosApp.xcodeproj               # then Run; set TEAM_ID in iosApp/Configuration/Config.xcconfig for a device
```

## Before going live

Ads run on Google's public test IDs. Every spot that needs a real ID is marked `TODO(admob-account)`:

```
grep -rn "TODO(admob-account)" ads iosApp --exclude-dir=build
```

See `docs/reports/2026-10-23-ads.md` for the full checklist.
