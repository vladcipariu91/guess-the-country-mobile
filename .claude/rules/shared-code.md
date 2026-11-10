---
paths:
  - "shared/src/**/*.kt"
  - "ads/src/**/*.kt"
  - "shared/build.gradle.kts"
  - "ads/build.gradle.kts"
---

# Shared (KMP) code rules

- **commonMain is the default.** No `android.*`, `java.*`, `platform.*` (UIKit/Foundation) or
  `kotlinx.cinterop` imports there. If a commonMain file needs one, the code belongs in a platform source set.
- **Pick the narrowest bridge:**
  - a single `@Composable` or function that needs a platform API → `expect fun` in commonMain, `actual fun`
    in `androidMain` and `iosMain` (e.g. `designsystem/motion/ReduceMotion*.kt`).
  - an object with platform dependencies → interface in commonMain, implementation in a platform set,
    bound in `platformModule` (`di/PlatformModule.<platform>.kt`).
  - a third-party SDK → its own module with an interface in commonMain (as `ads/` does).
- **Every `expect` needs an `actual` in both `androidMain` and `iosMain`.** File names end in
  `.android.kt` / `.ios.kt`. Room's `AppDatabaseConstructor` is the exception: KSP generates its actuals.
- **Swift implements Kotlin interfaces through callbacks**, never `suspend`. Wrap them in
  `suspendCancellableCoroutine` and resume only `if (continuation.isActive)` (see `IosAdService`).
- **Types Swift uses must be public** in `shared` or `ads`. `shared` re-exports `ads` with `export(project(":ads"))`.
  A new module that Swift calls needs the same `api` + `export` wiring.
- **Koin:** shared bindings go in `sharedModule`, platform ones in `platformModule`, and ViewModels use `viewModelOf(::X)`.
  iOS-only bindings that Swift owns are passed in through `setUpKoin`.
- **Room:** entities, DAOs and the database live in `data/db/`. Mapping stays in the repository file. A new
  table goes in `@Database(entities = …)` and the generated schema in `shared/schemas/` is committed.
- **Dependencies:** add them to `gradle/libs.versions.toml` and reference them as `libs.…` in the right
  source set (`commonMain.dependencies`, `androidMain.dependencies`, …).
