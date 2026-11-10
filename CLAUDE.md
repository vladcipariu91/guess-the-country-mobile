# Guess the Country

Kotlin Multiplatform geography quiz for Android and iOS, with the UI in Compose Multiplatform. This repo
is a sandbox for AI-assisted feature work: prefer small, verified, reviewable steps over big rewrites.

## Commands
- Build Android: `./gradlew :androidApp:assembleDebug`
- Link the iOS framework (simulator): `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
- Build the iOS app: `/run-app ios` (xcodebuild, scheme `iosApp`). Xcode's build phase runs
  `:shared:embedAndSignAppleFrameworkForXcode` itself.
- Unit tests, common code on the JVM: `./gradlew :shared:testAndroidHostTest :ads:testAndroidHostTest`
- Unit tests, iOS simulator (common tests + `iosTest`, slower): `./gradlew :shared:iosSimulatorArm64Test :ads:iosSimulatorArm64Test`
- One test class: `./gradlew :shared:testAndroidHostTest --tests "*HomeViewModelTest"`
- Everything: `./gradlew check` (all unit tests, the design checks and the `build-logic` tests)
- Lint: `./gradlew :androidApp:lintDebug`. No Kotlin formatter is configured (see Open questions).
- Install, launch and screenshot: `/run-app android` or `/run-app ios`

## Architecture
`ui/` → `presentation/` (ViewModels) → `domain/` (models + repository interfaces) ← `data/` (Room).
Everything is in `shared/src/commonMain/kotlin/me/vladcip/guessthecountry/`.
- `ui/`: Compose screens. `App()` is the shared entry point. It gets ViewModels with `koinViewModel()`
  and collects `state` with `collectAsStateWithLifecycle()`. There's no navigation library.
- `designsystem/`: theme, components, motion and the debug catalogue. They read the token objects
  that are generated from `design/tokens.json`.
- `presentation/`: `androidx.lifecycle.ViewModel`s exposing one `StateFlow<XUiState>`, either via
  `MutableStateFlow` + `update`/`copy`, or `combine(...).stateIn(viewModelScope, WhileSubscribed(5_000), ...)`.
- `domain/`: pure Kotlin rules and models, plus repository interfaces (e.g. `CountryRepository`).
- `data/`: Room entities, DAOs and `Room*Repository` implementations with private `toDomain()`/`toEntity()`
  mappers. Seeded content is in `data/seed/`.
- `di/Koin.kt`: `sharedModule` + `expect val platformModule`. `initKoin` is called from
  `GuessTheCountryApplication` (Android) and from `setUpKoin` in Swift (iOS).
- Platform code: `shared/src/androidMain` and `shared/src/iosMain` hold the `actual`s
  (`platformModule`, `rememberReduceMotion`), `databaseBuilder` and `MainViewController` (iOS).
- `ads/`: a separate KMP module. `AdService` is in commonMain. Android implements it with the Google SDK.
  On iOS `IosAdService` wraps `NativeRewardedAds`, which Swift implements (`iosApp/iosApp/GoogleRewardedAds.swift`).
  `shared` re-exports `ads` in the `Shared` framework.
- `androidApp/`: an Activity shell around `App()`. Debug builds add `CatalogueActivity`.
- `iosApp/`: SwiftUI shell hosting `MainViewController()`. Signing values are in `Configuration/Config.xcconfig`.
- `build-logic/`: an included build with the `gtc.design-tokens` plugin (codegen, export, design checks).

## Conventions
- commonMain by default. Platform source sets hold only what needs a platform API, behind an
  `expect`/`actual` or a Koin binding in `platformModule`.
- Third-party platform SDKs stay behind an interface in their own module (the way `ads` does it).
- Coroutines and Flow only: `suspend` for one-shot calls, `Flow` for streams. Launch work in `viewModelScope`.
- When catching `Exception` in a coroutine, rethrow `CancellationException` first.
- Nulls from storage are resolved in the repository mapper (e.g. `it?.toDomain() ?: PlayerProgress()`).
  The UI only sees domain models.
- Dependencies and plugins come from `gradle/libs.versions.toml`. Never hardcode versions in build files.
- New domain and ViewModel logic gets tests in `shared/src/commonTest`.
- Screen code uses design-system tokens and components, never raw colours, `dp`/`sp` literals or fonts.

## Gotchas
- `design/tokens.json` is the source of truth. The token Kotlin is generated into `shared/build/`, so
  never edit it there. A token change needs a republish to Claude Design and a new `design/tokens.published.sha256`
  (see `design/README.md`). Otherwise `checkTokensPublished` fails.
- `checkNoRawDesignValues` fails on raw design values outside `designsystem/`. `design/raw-values-baseline.txt`
  only shrinks: remove a file's entry once that file is clean, and never add one.
- Pre-release, Room stays at `version = 1` with no migrations. After a schema change, update
  `shared/schemas/` and uninstall the app before running it again.
- `AppDatabaseConstructor` is an `expect` whose actuals Room's KSP generates per target. Don't write them.
- Ads use Google's public test IDs. Real IDs replace each `TODO(admob-account)`. Never use real IDs while testing.
- The Stop hook leaves out the iOS simulator tests and the Swift build because they're slow. Run them
  yourself when you change `iosMain`, `iosTest` or `iosApp/`.

## Open questions
- Dispatchers aren't injected anywhere. Room hardcodes `Dispatchers.IO`, and tests replace only `Main`.
  Should new code inject them?
- Kotlin formatting: there's no ktlint/spotless/detekt config or `.editorconfig`. ktlint defaults flag
  about 800 issues in the existing code.
- Test names: `commonTest` uses backtick sentences, `iosTest` uses camelCase.
- UI strings are hardcoded in English. No string resources exist yet.

## Workflow
- For a new feature, run `/feature <description>`: explore, plan, implement in layers, test, verify.
- Before saying a task is done, show the test output. For UI changes, run `/run-app android` and
  `/run-app ios`, then look at the screenshots.
- For a UI bug, take a *before* screenshot with `/run-app <platform> 5 before-<name>` before touching any code.
- Use the `reviewer` subagent for a fresh-eyes review of the diff before reporting a feature as done.
- Finish every UI feature or bug fix with `/summary`, a short HTML page with screenshots (before/after
  for bugs), then hand it to the user to test.
- A Stop hook re-runs the JVM unit tests and compiles Android and iOS Kotlin when `.kt`, `.kts`, `.xml`
  or `.swift` files changed, and sends failures back.
- When replacing code, delete the old version rather than commenting it out (git keeps the history).
- Commit only when asked, on a branch, never on `main`.
