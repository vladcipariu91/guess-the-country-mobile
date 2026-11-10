---
paths:
  - "shared/src/commonMain/kotlin/**/ui/**/*.kt"
  - "shared/src/commonMain/kotlin/**/designsystem/**/*.kt"
  - "androidApp/src/**/*.kt"
  - "iosApp/**/*.swift"
---

# Compose Multiplatform UI rules

- UI is Compose Multiplatform in `shared` commonMain. `MainActivity` and the SwiftUI `ContentView` only
  host `App()`. Don't build screens in SwiftUI or Android Views.
- Only `App()` talks to ViewModels. It gets them with `koinViewModel()` and collects state with
  `collectAsStateWithLifecycle()`. Screens take state and event lambdas (`onGuess: (Country) -> Unit`).
- Navigation is a `when` over the ViewModel's UI state in `App()`. There's no navigation library.
  System back goes through `SystemBackHandler` (navigationevent).
- Keep logic out of composables. Derived values belong in the ViewModel or domain.
- Colours, type, sizes and motion come from `GtcTheme.colors`, `GtcTheme.typography`, the generated
  tokens and `designsystem/components`. Outside `designsystem/`, raw `Color(0x…)`, `dp`/`sp` literals,
  `Font(`/`FontFamily(` and `MaterialTheme.colorScheme`/`.typography` fail `checkNoRawDesignValues`.
  Only files listed in `design/raw-values-baseline.txt` are exempt.
- A new component goes in `designsystem/components/` and is added to `designsystem/catalogue/CatalogueScreen.kt`.
- Respect `rememberReduceMotion()` for any new animation.
- Insets: `App()` applies `safeDrawingPadding()`. New full-screen surfaces must handle insets too, on both
  platforms (`ContentView` uses `ignoresSafeArea()` and leaves insets to Compose).
- Fonts and files live in `shared/src/commonMain/composeResources/` and are read through the internal `Res` class.
- Loading, empty and error states should each be visible, not only the success path.
- Check UI changes on both platforms with `/run-app android` and `/run-app ios`.
