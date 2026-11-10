---
name: feature
description: Builds a new feature or behaviour change in this Kotlin Multiplatform app end to end - explore, plan, implement layer by layer (common first, platform only where needed), test, verify on Android and iOS, review. Use when the user asks to add or change user-facing functionality that touches more than one layer (domain, data, ViewModel, UI, platform).
argument-hint: "[feature description]"
---

# Build a feature: $ARGUMENTS

Work through these phases in order. Keep each message short.

## 1. Explore (no edits yet)
- Read the files the feature touches. The layer map is in CLAUDE.md.
- Decide, per change, **where it lives**:
  - commonMain, which is the default.
  - an `expect`/`actual` or a `platformModule` binding, when it needs a platform API.
  - a new module behind an interface, for a third-party SDK.
  - Swift in `iosApp/`, only when an iOS SDK is Swift-only.
  Follow `.claude/rules/shared-code.md`.
- If the feature needs a schema change, remember Room stays at version 1 pre-release (see CLAUDE.md).
- Note anything ambiguous in the request.
- If the feature changes an existing screen, capture baselines now with the `run-app` skill
  (`android 5 before-<name>` and `ios 5 before-<name>`), before any edits. They go in the summary.

## 2. Plan, then check in
Post a plan of at most 10 lines:
- the change per layer, bottom-up: domain model/rules → repository interface → Room entity/DAO/mapper →
  ViewModel state → composable → platform code (androidMain / iosMain / Swift), each marked common or platform
- the tests you'll add
- open questions or assumptions

Wait for the user's OK, unless they already said to go ahead.

## 3. Implement in small steps
- Go bottom-up, one layer at a time, and follow the patterns in CLAUDE.md and `.claude/rules/`.
- Model new UI state explicitly in the ViewModel's UI state: loading, error, empty and content where relevant.
- Wire new dependencies in `di/Koin.kt` (`sharedModule`) or in both `platformModule`s.
- Every new `expect` gets an `actual` on both platforms in the same step.
- Don't touch unrelated code. Don't upgrade dependencies unless the feature needs it, and say so if it does.

## 4. Test
- Add tests for new domain and ViewModel logic in `commonTest`, and for Room or Kotlin/Native-only code in
  `iosTest` (see `.claude/rules/testing.md`).
- Run `./gradlew :shared:testAndroidHostTest :ads:testAndroidHostTest` and fix any failure before moving on.
- If iOS code, `iosTest` or Room changed, also run `./gradlew :shared:iosSimulatorArm64Test`.
- If design tokens or screen styling changed, run `./gradlew check`.

## 5. Verify
- For UI changes, run the `run-app` skill for `android` and for `ios`, and read both screenshots. Check that the
  change is actually visible on both, and that nothing else on the screen regressed.
- Delegate a review of `git diff` to the `reviewer` subagent. Fix real issues and ignore style nits.

## 6. Summarise, then hand over for testing
- Run the `summary` skill. It builds an HTML page with the new screens on both platforms (and the
  before screens if any), where the change sits in the layers, the checks and the steps to try it.
- Reply in at most three lines: the summary path, anything not done or not verified, and a prompt
  for the user to test.
