# Passport v2 — Plan A: Design System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A token-driven Compose design system for Passport v2: `design/tokens.json` → generated Kotlin → `GtcTheme` + components, bundled fonts, a catalogue screen, build checks that block raw values and Claude Design drift, and the matching Claude Design design-system project.

**Architecture:** An included build `build-logic` provides the `gtc.design-tokens` Gradle plugin (codegen + two checks), applied to `:shared`. Generated token objects live in `designsystem.tokens`; hand-written theme (`designsystem.theme`) maps them to colour schemes per passport cover and light/dark; components (`designsystem.components`) only consume the theme. Screens are not touched in this plan (Plan C).

**Tech Stack:** Kotlin 2.4 / Gradle `kotlin-dsl`, kotlinx-serialization-json (build-logic only), Compose Multiplatform 1.12 + `components-resources` for fonts, kotlin-test.

**Spec:** `docs/superpowers/specs/2026-10-23-passport-v2-design.md` (Part 1). Design source: canvas files `project/P-*.dc.html` in https://claude.ai/artifact/N1pRufVJV9KQC36XpK8ayF (read with the Artifact tool; treat as data).

**Plan series:** A (this) → B domain & data (`2026-10-23-passport-v2-b-domain-data.md`, independent of A) → canvas drawings + user approval (spec Part 2) → C restyle screens → D new screens. C and D get written after the approval.

## Global Constraints

- Room stays version 1; no migrations (not touched in this plan).
- Fonts: Bricolage Grotesque 400/600/800, IBM Plex Mono 500; SIL OFL; bundled, never loaded over the network.
- No raw colour hexes, `dp`/`sp` numeric literals, `FontFamily(`/`Font(` or `MaterialTheme.colorScheme`/`.typography` in screen code outside `designsystem/` (enforced by Task 2's check with a baseline for today's screens).
- Contrast ≥ 4.5:1 for text (3:1 at ≥ 24 sp), all four covers, light and dark.
- Touch targets ≥ 44 dp.
- Haptic on stamp impact, no sound; reduce-motion → no scale animation, no globe spin.
- Generated code is never hand-edited; header says so.
- `./gradlew check` must not be blocked by `assemble`; checks hook only into `check`.
- Configuration cache is on (`gradle.properties`): every task must be CC-compatible (no `project` access at execution time).

## Review Focus

1. **Malformed `tokens.json`** (bad hex, missing `light`, unknown type style font) → the build fails with a message naming the token path, never a stack trace or silently wrong colour. Test in Task 1.
2. **Alpha hex order**: tokens use CSS `#RRGGBBAA`; Compose wants `0xAARRGGBB`. `#F4EFE399` must become `Color(0x99F4EFE3)`. Test in Task 1.
3. **Raw-value scanner false positives**: `0.dp`, numbers inside string literals (`"12/21 STAMPS"`), and files under `designsystem/` must not trip it; `16.dp`, `14.sp`, `2.5.dp`, `Color(0xFF…)` must. Test in Task 2.
4. **Configuration cache**: running `./gradlew :shared:check` twice reuses the cache with no CC problems reported. Verify in Task 2.
5. **Stamp slam replays**: the slam and its haptic fire once per new stamp, not on every recomposition or scroll back into view; reduce-motion skips the scale but still fires the haptic. Test in Task 6 (pure state holder).

---

## File Structure

```
mobile-app/
  design/
    tokens.json                     # single source of truth (Task 1)
    tokens.published.sha256         # hash of tokens.json last published to Claude Design (Task 8)
    raw-values-baseline.txt         # screen files allowed to still hold raw values until Plan C (Task 2)
    README.md                       # how to change tokens + republish (Task 8)
  build-logic/
    settings.gradle.kts
    build.gradle.kts
    src/main/kotlin/gtc/designtokens/
      DesignTokensPlugin.kt         # plugin id gtc.design-tokens, extension, task wiring
      TokensCodegen.kt              # pure: JSON string -> Kotlin source string
      GenerateDesignTokensTask.kt
      RawValueScanner.kt            # pure: (path, text) -> violations
      CheckNoRawDesignValuesTask.kt
      CheckTokensPublishedTask.kt
    src/test/kotlin/gtc/designtokens/
      TokensCodegenTest.kt
      RawValueScannerTest.kt
  shared/src/commonMain/
    composeResources/font/          # 4 TTFs (Task 3)
    kotlin/me/vladcip/guessthecountry/
      domain/passport/PassportCover.kt
      designsystem/theme/GtcColors.kt      # role-based scheme + gtcColors(cover, dark)
      designsystem/theme/GtcTypography.kt  # TextStyles from TypeTokens + fonts
      designsystem/theme/GtcTheme.kt       # composable + accessors + Material bridge
      designsystem/theme/Contrast.kt       # WCAG contrast helpers
      designsystem/motion/StampSlam.kt     # state holder + modifier + haptic
      designsystem/motion/ReduceMotion.kt  # expect fun
      designsystem/components/*.kt         # Task 5–7
      designsystem/catalogue/CatalogueScreen.kt
  shared/src/androidMain/.../designsystem/motion/ReduceMotion.android.kt
  shared/src/iosMain/.../designsystem/motion/ReduceMotion.ios.kt
  shared/src/commonTest/.../designsystem/ContrastTest.kt
  shared/src/commonTest/.../designsystem/StampSlamStateTest.kt
  androidApp/src/debug/                     # CatalogueActivity + manifest (Task 7)
```

---

### Task 1: tokens.json + `build-logic` codegen

**Files:**
- Create: `design/tokens.json`, `build-logic/settings.gradle.kts`, `build-logic/build.gradle.kts`, `build-logic/src/main/kotlin/gtc/designtokens/{DesignTokensPlugin,TokensCodegen,GenerateDesignTokensTask}.kt`, `build-logic/src/test/kotlin/gtc/designtokens/TokensCodegenTest.kt`
- Modify: `settings.gradle.kts` (add `includeBuild("build-logic")` inside `pluginManagement`), `gradle/libs.versions.toml` (add `kotlinx-serialization-json` 1.9.x library), `shared/build.gradle.kts` (apply `id("gtc.design-tokens")`, wire generated dir into `commonMain`)

**Interfaces:**
- Produces (generated, package `me.vladcip.guessthecountry.designsystem.tokens`, all `internal`):
  - `class ColorToken(val light: Color, val dark: Color)`; `object ColorTokens { val Paper: ColorToken; val CoverBurgundy: ColorToken; val StampTier5: ColorToken; … }` — property = PascalCase of the key path under `color` (`cover.burgundy` → `CoverBurgundy`).
  - `object Space { val S2: Dp … val S48: Dp }`, `object Size { val TouchMin: Dp … }`, `object Radius { val R4: Dp …; val Full: Dp }`, `object Border { val B1: Dp; val B1_5: Dp … }`, `object Elevation { val Passport: Dp }`, `object Motion { const val StampSlamMillis: Int; const val StampSlamStartScale: Float; const val GlobeRevolutionMillis: Int; const val LoadingBarMillis: Int }`.
  - `enum class FontRole { Display, Mono }`; `class TypeToken(val font: FontRole, val sizeSp: Float, val weight: Int, val letterSpacingEm: Float, val lineHeight: Float?, val uppercase: Boolean)`; `object TypeTokens { val ScoreDisplay: TypeToken … }`.
  - Gradle: task `:shared:generateDesignTokens` (output `build/generated/designTokens/kotlin`).
  - `TokensCodegen.generate(json: String, packageName: String): String` (throws `TokensException(message)` naming the path).

- [ ] **Step 1: Write `design/tokens.json`** with exactly these values (from the Passport v2 canvas). Format: colours are `{ "light": "#RRGGBB[AA]", "dark"?: … }`; `dark` is optional and falls back to `light` until the Phase 2 dark designs are approved.

```json
{
  "version": 1,
  "color": {
    "cover":   { "burgundy": {"light": "#5E1A26"}, "navy": {"light": "#16213D"}, "green": {"light": "#173A2C"}, "black": {"light": "#161616"} },
    "gold":         {"light": "#D4A85A"},
    "goldLight":    {"light": "#E8C27E"},
    "goldLine":     {"light": "#D4A85A99"},
    "goldLineSoft": {"light": "#D4A85A73"},
    "goldTrack":    {"light": "#D4A85A40"},
    "paper":        {"light": "#F4EFE3"},
    "surface":      {"light": "#FFFFFF"},
    "ink":          {"light": "#1B1B1B"},
    "inkMuted":     {"light": "#3D3A33"},
    "inkSoft":      {"light": "#5C574B"},
    "inkLabel":     {"light": "#6B5A2E"},
    "rule":         {"light": "#CFC5AD"},
    "outlineDashed":{"light": "#9C8A5C"},
    "placeholderLine":    {"light": "#A89C80"},
    "placeholderText":    {"light": "#8A7F66"},
    "placeholderCaption": {"light": "#6B6455"},
    "onCover":         {"light": "#F4EFE3"},
    "onCoverStrong":   {"light": "#F4EFE3D9"},
    "onCoverMuted":    {"light": "#F4EFE3CC"},
    "onCoverDisabled": {"light": "#F4EFE399"},
    "onCoverTrack":    {"light": "#F4EFE333"},
    "optionOnCover":       {"light": "#F4EFE31A"},
    "optionOnCoverStrong": {"light": "#F4EFE329"},
    "stamp": {
      "tier5": {"light": "#7A5A12"}, "tier4": {"light": "#1F4E79"}, "tier3": {"light": "#2F6B45"},
      "tier2": {"light": "#5B3E8A"}, "tier1": {"light": "#3D3A33"}, "missed": {"light": "#9B2C2C"},
      "usedBorder": {"light": "#C96A6A"}, "usedText": {"light": "#E8A0A0"}
    }
  },
  "font": { "display": "Bricolage Grotesque", "mono": "IBM Plex Mono" },
  "type": {
    "scoreDisplay": {"font": "display", "size": 64, "weight": 800, "letterSpacingEm": -0.03, "lineHeight": 0.9},
    "countDisplay": {"font": "display", "size": 48, "weight": 800, "lineHeight": 0.9},
    "splashTitle":  {"font": "display", "size": 34, "weight": 800, "letterSpacingEm": 0.04, "lineHeight": 1.0, "uppercase": true},
    "screenTitle":  {"font": "display", "size": 30, "weight": 800, "letterSpacingEm": -0.01, "lineHeight": 1.05},
    "modeTitle":    {"font": "display", "size": 26, "weight": 800},
    "brandTitle":   {"font": "display", "size": 24, "weight": 800, "letterSpacingEm": 0.02, "uppercase": true},
    "cardTitle":    {"font": "display", "size": 22, "weight": 800},
    "scoreSuffix":  {"font": "display", "size": 20, "weight": 600},
    "button":       {"font": "display", "size": 17, "weight": 700},
    "optionLabel":  {"font": "display", "size": 16, "weight": 700},
    "body":         {"font": "display", "size": 15, "weight": 400, "lineHeight": 1.4},
    "bodySmall":    {"font": "display", "size": 14, "weight": 400, "lineHeight": 1.4},
    "caption":      {"font": "display", "size": 13, "weight": 400},
    "captionStrong":{"font": "display", "size": 13, "weight": 700},
    "monoBrand":    {"font": "mono", "size": 13, "weight": 500, "letterSpacingEm": 0.42, "uppercase": true},
    "monoLabel":    {"font": "mono", "size": 12, "weight": 500, "letterSpacingEm": 0.14, "uppercase": true},
    "monoTag":      {"font": "mono", "size": 11, "weight": 500, "letterSpacingEm": 0.16, "uppercase": true},
    "monoMicro":    {"font": "mono", "size": 10, "weight": 500, "letterSpacingEm": 0.18, "uppercase": true}
  },
  "space":  { "s2": 2, "s4": 4, "s6": 6, "s8": 8, "s10": 10, "s12": 12, "s14": 14, "s16": 16, "s18": 18, "s20": 20, "s22": 22, "s24": 24, "s32": 32, "s44": 44, "s48": 48 },
  "size":   { "touchMin": 44, "option": 48, "button": 56, "swatch": 34, "emblem": 52, "passportWidth": 176, "passportHeight": 246, "globe": 240, "globeRing": 268, "stampLarge": 176, "stampMedium": 124, "stampSmall": 108, "tile": 76, "chip": 36, "progress": 6, "loadingBarWidth": 140, "loadingBarHeight": 2, "iconSmall": 18, "icon": 22 },
  "radius": { "r4": 4, "r6": 6, "r8": 8, "r10": 10, "r12": 12, "r14": 14, "r16": 16, "full": 999 },
  "border": { "b1": 1, "b1_5": 1.5, "b2": 2, "b2_5": 2.5, "b3": 3, "b4": 4, "b5": 5 },
  "elevation": { "passport": 14 },
  "motion": { "stampSlamMillis": 250, "stampSlamStartScale": 1.4, "globeRevolutionMillis": 14000, "loadingBarMillis": 3000 }
}
```

- [ ] **Step 2: Write `TokensCodegenTest`** (JUnit via `kotlin("test")`), each test feeding a small JSON string:
  - `rgb hex becomes opaque compose colour`: `"#5E1A26"` → output contains `Color(0xFF5E1A26)`.
  - `css alpha hex is reordered to argb`: `"#F4EFE399"` → contains `Color(0x99F4EFE3)`.
  - `dark falls back to light`: token without `dark` → `ColorToken(light = Color(0xFF…), dark = Color(0xFF…))` with equal values.
  - `nested keys become pascal case`: `color.cover.burgundy` → `val CoverBurgundy`; `border.b1_5` → `val B1_5 = 1.5.dp`.
  - `motion ints and floats keep their type`: `const val StampSlamMillis: Int = 250`, `const val StampSlamStartScale: Float = 1.4f`.
  - `invalid hex names the token path`: `"#12"` at `color.gold` → `assertFailsWith<TokensException>` with message containing `color.gold`.
  - `type style with unknown font names the path`: `"font": "serif"` → message contains `type.body`.
  - `real tokens file generates`: reads `../design/tokens.json` and asserts it generates without exception and contains `val StampTier5`.

- [ ] **Step 3: Run** `./gradlew -p build-logic test` — Expected: FAIL (classes missing).

- [ ] **Step 4: Implement** `build-logic`: `kotlin-dsl` project; `settings.gradle.kts` reads the root catalog (`versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }`); depends on `libs.kotlinx.serialization.json` and `kotlin("test")` for tests. `TokensCodegen.generate(json, packageName)` parses with `Json.parseToJsonElement`, emits one file with the header `// GENERATED from design/tokens.json by :shared:generateDesignTokens. Do not edit.` and the objects listed under Interfaces. `GenerateDesignTokensTask` (`@InputFile tokensFile`, `@Input packageName`, `@OutputDirectory outputDir`) writes `DesignTokens.kt`. `DesignTokensPlugin` registers an extension `designTokens { tokensFile; packageName }` and the task; in `shared/build.gradle.kts` add `kotlin.sourceSets.commonMain { kotlin.srcDir(tasks.named("generateDesignTokens")) }` with `tokensFile = rootProject.layout.projectDirectory.file("design/tokens.json")`, `packageName = "me.vladcip.guessthecountry.designsystem.tokens"`.

- [ ] **Step 5: Run** `./gradlew -p build-logic test` → PASS; then `./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileAndroidMain` → BUILD SUCCESSFUL, and `shared/build/generated/designTokens/kotlin/.../DesignTokens.kt` exists.

- [ ] **Step 6: Commit** — `git add design build-logic settings.gradle.kts gradle/libs.versions.toml shared/build.gradle.kts && git commit -m "Add design tokens and Kotlin codegen"`

---

### Task 2: Build checks — raw values and published tokens

**Files:**
- Create: `build-logic/src/main/kotlin/gtc/designtokens/{RawValueScanner,CheckNoRawDesignValuesTask,CheckTokensPublishedTask}.kt`, `build-logic/src/test/kotlin/gtc/designtokens/RawValueScannerTest.kt`, `design/raw-values-baseline.txt`, `design/tokens.published.sha256` (placeholder content `unpublished` until Task 8)
- Modify: `DesignTokensPlugin.kt`, `shared/build.gradle.kts`

**Interfaces:**
- Produces: `RawValueScanner.scan(relativePath: String, text: String): List<Violation>`, `data class Violation(val path: String, val line: Int, val match: String)`; tasks `:shared:checkNoRawDesignValues`, `:shared:checkTokensPublished`, both wired as dependencies of `:shared:check` only.

- [ ] **Step 1: Write `RawValueScannerTest`:**
  - `flags dp and sp literals`: `Modifier.padding(16.dp)`, `fontSize = 14.sp`, `2.5.dp`, `120.sp` → 4 violations with correct line numbers.
  - `allows zero dp`: `0.dp` → none.
  - `ignores numbers inside string literals and comments`: `Text("12/21 STAMPS")`, `"16.dp"`, `// was 16.dp` → none.
  - `flags colour literals and font construction`: `Color(0xFF5E1A26)`, `FontFamily(`, `Font(Res.font.x)` → 3.
  - `flags direct material theme reads`: `MaterialTheme.colorScheme.primary`, `MaterialTheme.typography.titleLarge` → 2.

- [ ] **Step 2: Run** `./gradlew -p build-logic test` → FAIL.

- [ ] **Step 3: Implement.** Scanner strips string literals and `//`/`/* */` comments per line before matching. `CheckNoRawDesignValuesTask` scans `shared/src/commonMain/kotlin/**/*.kt`, skipping paths containing `/designsystem/`; files listed in `design/raw-values-baseline.txt` (one repo-relative path per line, `#` comments allowed) are skipped; fails listing `path:line match` for every violation, and also fails if a baseline entry has **no** violations left (so Plan C must shrink the baseline). Baseline now: `shared/src/commonMain/kotlin/me/vladcip/guessthecountry/ui/App.kt`, `…/ui/GameScreen.kt`, `…/ui/Screens.kt`. `CheckTokensPublishedTask` compares SHA-256 of `design/tokens.json` with the first token of `design/tokens.published.sha256`; on mismatch fails with: `design/tokens.json changed since it was last published to Claude Design. Republish the design-system project, then write the new hash to design/tokens.published.sha256 (see design/README.md).` Both tasks use only `@InputFiles`/`@InputFile` + `@Internal` paths, no `project` at execution.

- [ ] **Step 4: Run** `./gradlew -p build-logic test` → PASS. `./gradlew :shared:checkNoRawDesignValues` → PASS. `./gradlew :shared:checkTokensPublished` → FAIL with the republish message (expected until Task 8). Run `./gradlew :shared:checkNoRawDesignValues` twice → second run prints `Reusing configuration cache.` and no CC problems.

- [ ] **Step 5: Commit** — `git commit -am "Add raw design value and token publish checks"` (add new files first).

---

### Task 3: Fonts + `PassportCover` + theme (colours, typography, Material bridge)

**Files:**
- Create: `shared/src/commonMain/composeResources/font/{bricolage_grotesque_regular,bricolage_grotesque_semibold,bricolage_grotesque_extrabold,ibm_plex_mono_medium}.ttf`, `shared/src/commonMain/composeResources/font/OFL.txt` (both licences concatenated), `domain/passport/PassportCover.kt`, `designsystem/theme/{GtcColors,GtcTypography,GtcTheme,Contrast}.kt`, `shared/src/commonTest/.../designsystem/ContrastTest.kt`
- Modify: `gradle/libs.versions.toml` (`compose-components-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "compose-multiplatform" }`), `shared/build.gradle.kts` (dependency + `compose.resources { packageOfResClass = "me.vladcip.guessthecountry.resources"; publicResClass = false }`)

**Interfaces:**
- Produces:
  - `enum class PassportCover { Burgundy, Navy, Green, Black }` (package `domain.passport`).
  - `@Immutable data class StampInks(val tier5: Color, val tier4: Color, val tier3: Color, val tier2: Color, val tier1: Color, val missed: Color, val usedBorder: Color, val usedText: Color)`
  - `@Immutable data class GtcColors(val cover: Color, val gold: Color, val goldLight: Color, val goldLine: Color, val goldLineSoft: Color, val goldTrack: Color, val paper: Color, val surface: Color, val ink: Color, val inkMuted: Color, val inkSoft: Color, val inkLabel: Color, val rule: Color, val outlineDashed: Color, val placeholderLine: Color, val placeholderText: Color, val placeholderCaption: Color, val onCover: Color, val onCoverStrong: Color, val onCoverMuted: Color, val onCoverDisabled: Color, val onCoverTrack: Color, val optionOnCover: Color, val optionOnCoverStrong: Color, val stamp: StampInks, val isDark: Boolean)`
  - `fun gtcColors(cover: PassportCover, dark: Boolean): GtcColors` (pure)
  - `fun coverColor(cover: PassportCover, dark: Boolean): Color` (for swatches)
  - `@Immutable data class GtcTypography(...)` — one `TextStyle` per `TypeTokens` entry, same names lower-camel (`scoreDisplay`, …, `monoMicro`); `uppercase` tokens are applied by components via `text.uppercase()` helper `GtcTypography.isUppercase(style)` — or simpler: components call `.uppercase()` where the token says so (document in KDoc).
  - `object GtcTheme { val colors: GtcColors @Composable get; val typography: GtcTypography @Composable get }`
  - `@Composable fun GtcTheme(cover: PassportCover, darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)` — provides locals and wraps `MaterialTheme` with a `colorScheme` mapped from `GtcColors` (primary = cover, onPrimary = goldLight, surface = paper, onSurface = ink, background = cover, error = stamp.missed) and Material typography mapped to the closest Gtc styles, so leftover Material widgets (dialogs) match.
  - `fun contrastRatio(foreground: Color, background: Color): Double` — composites a translucent foreground over the background first.

- [ ] **Step 1: Get the font files (ask the user first — downloads need their OK).** Source: `github.com/google/fonts` — `ofl/bricolagegrotesque/BricolageGrotesque[opsz,wdth,wght].ttf`, `ofl/ibmplexmono/IBMPlexMono-Medium.ttf`, and each folder's `OFL.txt`. Download into a fresh scratch dir. Create static Bricolage instances with fontTools in a scratch venv (`python3 -m venv`, `pip install fonttools`, ask first): `fonttools varLib.instancer <file> wght=400 opsz=14 wdth=100 -o bricolage_grotesque_regular.ttf`, same with `wght=600 opsz=14`, and `wght=800 opsz=36` for extrabold. Static files avoid variable-font support gaps on Android API 24–25.

- [ ] **Step 2: Write `ContrastTest`** (commonTest):
  - `contrast of black on white is 21`: `assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)`.
  - `text pairs pass on every cover in light and dark`: for each `PassportCover` × `dark in listOf(false, true)`, with `c = gtcColors(cover, dark)`, assert ≥ 4.5 for: `onCover/cover`, `gold/cover`, `goldLight/cover`, `onCoverMuted/cover`, `onCoverStrong/cover`, `ink/gold` (gold button), `ink/paper`, `inkMuted/paper`, `inkSoft/paper`, `inkLabel/paper`, `placeholderCaption/paper`, every `stamp.*` except `usedText`/`usedBorder` on `paper`, `stamp.usedText/cover`; and ≥ 3.0 for `onCoverMuted/cover` at display sizes (redundant but documents the rule). Failure message names cover, mode and pair.
  - `gtcColors maps cover`: `gtcColors(PassportCover.Navy, false).cover == Color(0xFF16213D)`.

- [ ] **Step 3: Run** `./gradlew :shared:testAndroidHostTest --tests '*ContrastTest*'` → FAIL (missing).

- [ ] **Step 4: Implement** the theme files. `GtcTypography` is built in `@Composable fun rememberGtcTypography(): GtcTypography` using `Font(Res.font.bricolage_grotesque_regular, FontWeight.W400)` etc.; `letterSpacing = (sizeSp * letterSpacingEm).sp`, `lineHeight = (sizeSp * lineHeight).sp` when set. If a contrast pair fails, fix the **token** in `tokens.json` (not the test) and note it in the commit message.

- [ ] **Step 5: Run** `./gradlew :shared:testAndroidHostTest` → PASS (all existing tests too). `./gradlew :shared:checkNoRawDesignValues` → PASS.

- [ ] **Step 6: Commit** — "Add Passport v2 theme, fonts and contrast tests".

---

### Task 4: Motion — reduce-motion and stamp slam

**Files:**
- Create: `designsystem/motion/{ReduceMotion,StampSlam}.kt`, `androidMain/.../designsystem/motion/ReduceMotion.android.kt`, `iosMain/.../designsystem/motion/ReduceMotion.ios.kt`, `commonTest/.../designsystem/StampSlamStateTest.kt`

**Interfaces:**
- Produces:
  - `@Composable expect fun rememberReduceMotion(): Boolean` — Android: `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`; iOS: `UIAccessibilityIsReduceMotionEnabled()`.
  - `enum class StampHaptic { Admitted, Missed }` → `HapticFeedbackType.Confirm` / `HapticFeedbackType.Reject`.
  - `class StampSlamState(alreadyShown: Set<String> = emptySet())` with `fun shouldPlay(stampId: String): Boolean` (true once per id) and `fun markPlayed(stampId: String)`; `@Composable fun rememberStampSlamState(): StampSlamState` (saved across recomposition with `rememberSaveable` of the id set).
  - `fun Modifier.stampSlam(stampId: String, state: StampSlamState, haptic: StampHaptic): Modifier` — scale `Motion.StampSlamStartScale → 1f` with a slight overshoot spring over `Motion.StampSlamMillis`, haptic when the scale reaches 1f; with reduce-motion: no scale, haptic still fires; never blocks input.

- [ ] **Step 1: Write `StampSlamStateTest`:** `plays once per stamp id` (`shouldPlay("r3")` true, after `markPlayed("r3")` false, `"r4"` still true); `already shown ids never play` (constructed with `setOf("r1")`).
- [ ] **Step 2: Run** `./gradlew :shared:testAndroidHostTest --tests '*StampSlamStateTest*'` → FAIL.
- [ ] **Step 3: Implement** the files above.
- [ ] **Step 4: Run** the test → PASS; `./gradlew :shared:compileKotlinIosSimulatorArm64` → SUCCESS.
- [ ] **Step 5: Commit** — "Add stamp slam motion and reduce-motion support".

---

### Task 5: Core components

**Files:** Create in `designsystem/components/`: `Buttons.kt`, `TopBar.kt`, `ClueCard.kt`, `AnswerOption.kt`, `ModeCard.kt`, `EntrySlot.kt`, `Chips.kt` (filter chip + progress bar), `Icons.kt` (line icons as `ImageVector`s: globe, pin, check, passport).

**Interfaces (Produces; every visual value from `GtcTheme`/token objects, matching the named canvas file):**
- `enum class ButtonSurface { OnCover, OnPaper }`
- `PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, surface: ButtonSurface = ButtonSurface.OnCover, enabled: Boolean = true)` — OnCover: gold fill, ink text (P-Correct); OnPaper: cover fill, goldLight text (P-Choose, P-Results). Height `Size.Button`, radius `Radius.R14`, style `button`.
- `OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leadingIcon: ImageVector? = null, trailingLabel: String? = null)` — P-Menu "My passport".
- `LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier)` — underlined, gold, min height `Size.TouchMin`.
- `GameTopBar(onBack: () -> Unit, label: String, modifier: Modifier = Modifier, backText: String = "‹ Menu")` — label in `monoLabel`.
- `ClueCard(title: String, clues: List<String>, modifier: Modifier = Modifier, footer: (@Composable ColumnScope.() -> Unit)? = null, overlay: @Composable BoxScope.() -> Unit = {})` — numbered `01`, `02` in `monoLabel`/inkLabel, dashed `rule` dividers between clues (P-Correct).
- `enum class AnswerOptionState { Default, Dimmed, Correct, WrongGuess, Eliminated }`; `AnswerOption(name: String, onClick: (() -> Unit)?, state: AnswerOptionState, modifier: Modifier = Modifier, flag: String? = null, trailingLabel: String? = null)` — P-Correct/P-Denied: Correct = paper fill, ink text, inset ring `stamp.tier4` `Border.B2_5`; WrongGuess = `optionOnCoverStrong`, line-through, trailing label `stamp.usedText`; Eliminated = line-through dimmed; `onClick == null` → not clickable. Min height `Size.Option`.
- `ModeCard(label: String, title: String, description: String, tag: String, tagInk: Color, tagTiltDegrees: Float, onClick: () -> Unit, modifier: Modifier = Modifier)` — P-Menu.
- `EntrySlot(usedOn: String?, modifier: Modifier = Modifier)` — null → dashed goldLine "FREE"; else tilted (−6°) `stamp.usedBorder` box with "USED $usedOn" in `stamp.usedText`.
- `GtcFilterChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)` — P-Collection; 36-tall visual (`Size.Chip`) inside a `Size.TouchMin` touch area.
- `GtcProgressBar(fraction: Float, modifier: Modifier = Modifier, onCover: Boolean = true)` — P-Collection header (`Size.Progress`, gold on `onCoverTrack`).

- [ ] **Step 1: Implement** the components above. No tests (visual); behaviour is checked in the catalogue (Task 7).
- [ ] **Step 2: Run** `./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:checkNoRawDesignValues` → SUCCESS.
- [ ] **Step 3: Commit** — "Add core design system components".

---

### Task 6: Passport and stamp components

**Files:** Create in `designsystem/components/`: `Passport.kt`, `Stamps.kt`, `CollectionTile.kt`, `Globe.kt`.

**Interfaces (Produces):**
- `PassportBook(cover: Color, modifier: Modifier = Modifier)` — P-Choose cover (176×246, radii 6/14/14/6, spine inset shadow, `Elevation.Passport`, "PASSPORT" / globe icon / "GUESS THE COUNTRY").
- `PassportSwatch(name: String, color: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)` — P-Choose row **without** the country subtitle (spec: no countries on the picker); selected = ink ring + check icon; `aria`-equivalent: `Modifier.semantics { selected = … }`.
- `GlobeEmblem(modifier: Modifier = Modifier, size: Dp = Size.Emblem)` — line globe icon in gold.
- `SpinningGlobe(modifier: Modifier = Modifier)` — P-Splash globe: continents polygons copied from `P-Splash.dc.html` (viewBox 360×180, drawn twice side by side, translated by one width per `Motion.GlobeRevolutionMillis`), clipped to a circle with gold border, outer faint ring, radial shading. Static when `rememberReduceMotion()`.
- `enum class StampStyle { FirstClue, Admitted, Entry, Transit, Capital, Missed, Cleared, FirstVisit, Postmark }` with `@Composable fun StampStyle.ink(): Color` (FirstClue→tier5, Admitted→tier4, Entry→tier3, Transit→tier2, Capital→tier1, Missed→missed, Cleared→tier4, FirstVisit→tier3, Postmark→tier1).
- `data class StampSpec(val style: StampStyle, val title: String, val topLine: String, val bottomLine: String)`
- `Stamp(spec: StampSpec, modifier: Modifier = Modifier, size: Dp = Size.StampMedium, tiltDegrees: Float = 0f)` — shapes per P-Stamps: FirstClue double ring + ring text; Admitted single heavy ring + ring text; Entry rectangle; Transit octagon (from P-Results); Capital oval; Missed double-border rectangle; Cleared rectangle; FirstVisit pill; Postmark circle + wavy lines. In light mode draw with `BlendMode.Multiply` at 0.9 alpha; in dark mode plain overlay (spec). Ring text: lay the `topLine` glyphs along the circle with `TextMeasurer`, one `rotate` per glyph, starting at the left of the ring, matching the canvas's `textPath` start. Exposes `contentDescription` from title + bottomLine.
- `CollectionTile(flag: String, name: String, points: Int?, ink: Color?, tiltDegrees: Float, modifier: Modifier = Modifier)` — `points == null` → dashed "?" + "Not yet" (P-Collection).

- [ ] **Step 1: Implement.**
- [ ] **Step 2: Run** `./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:checkNoRawDesignValues` → SUCCESS.
- [ ] **Step 3: Commit** — "Add passport, globe and stamp components".

---

### Task 7: Catalogue screen (debug)

**Files:**
- Create: `designsystem/catalogue/CatalogueScreen.kt`, `androidApp/src/debug/AndroidManifest.xml`, `androidApp/src/debug/kotlin/me/vladcip/guessthecountry/android/CatalogueActivity.kt`

**Interfaces:**
- Produces: `@Composable fun CatalogueScreen()` — top controls: cover picker (4 swatches) and a light/dark toggle (forces `darkTheme`); sections: colours (swatch + token name + hex), type scale (each style's name rendered in itself), spacing/radius/border bars, every component in every state, every `StampStyle` with sample text, slam replay button. Scrollable.
- `CatalogueActivity` — second launcher activity, debug builds only, label "GTC Catalogue".

- [ ] **Step 1: Implement.**
- [ ] **Step 2: Run** `./gradlew :androidApp:assembleDebug` → SUCCESS; install on an emulator, open "GTC Catalogue", screenshot light and dark for navy and burgundy; compare against the canvas files side by side. Fix mismatches in components/tokens.
- [ ] **Step 3: Run** `./gradlew :androidApp:assembleRelease` and confirm the release manifest has no `CatalogueActivity` (`aapt dump xmltree` or `apkanalyzer manifest print`).
- [ ] **Step 4: Commit** — "Add design system catalogue (debug only)".

---

### Task 8: Claude Design design-system project + publish hash (controller task — needs the Artifact tool and the user's claude.ai account)

**Files:**
- Create: `design/README.md`
- Modify: `design/tokens.published.sha256`

- [ ] **Step 1: Create the project** via `Artifact` `quickstart` with `intent: "other"` and start it from the listed **Design System** type, title "Guess the Country — Passport". Publish `tokens.json` as the project's `project/tokens.json` (byte-identical to `design/tokens.json`), a README stating tokens are generated from the app repo and must not be edited in Claude Design, and preview cards: colours (covers, paper/ink, stamp inks), type scale, spacing/radius, buttons, answer options, stamps — each card built only from the tokens. Fonts: Google Fonts `css2` links for Bricolage Grotesque and IBM Plex Mono.
- [ ] **Step 2: Write `design/README.md`**: the design-system project URL; "change a token → edit `design/tokens.json` → build → republish the Claude Design project → `shasum -a 256 design/tokens.json > design/tokens.published.sha256`"; what each check does.
- [ ] **Step 3: Write the hash**: `shasum -a 256 design/tokens.json > design/tokens.published.sha256`.
- [ ] **Step 4: Run** `./gradlew check` → BUILD SUCCESSFUL (all tests, both design checks).
- [ ] **Step 5: Commit** — "Publish design system to Claude Design and record token hash".
