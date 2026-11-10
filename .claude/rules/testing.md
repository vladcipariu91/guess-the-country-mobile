---
paths:
  - "shared/src/*Test/**/*.kt"
  - "ads/src/*Test/**/*.kt"
  - "shared/build.gradle.kts"
  - "ads/build.gradle.kts"
---

# Unit test rules

Tests use `kotlin.test`, `kotlinx-coroutines-test` and Turbine. Shared fixtures are in
`shared/src/commonTest/kotlin/**/testing/`: `Fakes.kt` (`Fake*Repository`, `FakeToday`, `FakeAdService`)
and `TestCountries.kt` (`testCountry(n)`, `testCountries(count)`). Reuse them instead of copying them.

- **Where tests go:**
  - `commonTest` for domain, mappers and ViewModels. It runs on the JVM and on the iOS simulator.
  - `iosTest` for Kotlin/Native-only code and real Room (`Room.inMemoryDatabaseBuilder`).
  - `androidHostTest` would hold Android-only tests. None exist yet.
- **Fakes over mocks.** There's no mocking library. Extend a fake in `Fakes.kt` or write a small fake
  rather than adding one. Fakes follow the same rules as the real implementation.
- **Main dispatcher.** ViewModel tests call `Dispatchers.setMain(UnconfinedTestDispatcher())` in
  `@BeforeTest` and `Dispatchers.resetMain()` in `@AfterTest`, and run in `runTest { }`.
- **StateFlow.** Assert emissions with Turbine (`vm.state.test { awaitItem() }`), or read `state.value`
  for plain `MutableStateFlow` state. Never sleep.
- **Time and dates.** Inject `FakeToday` (`advanceOneDay()`) instead of reading the clock.
- **Annotations** come from `kotlin.test` (`@Test`, `@BeforeTest`, `@AfterTest`), not JUnit, so tests stay common.
- **Naming.** Test classes are `<ClassUnderTest>Test`. `commonTest` names use backticks and describe
  behaviour, e.g. `` `refresh picks up a new day` ``. `iosTest` currently uses camelCase.
- **Running.**
  - one class: `./gradlew :shared:testAndroidHostTest --tests "*<ClassName>"`
  - one class on iOS: `./gradlew :shared:iosSimulatorArm64Test --tests "*<ClassName>"`
  - everything: `./gradlew check`
