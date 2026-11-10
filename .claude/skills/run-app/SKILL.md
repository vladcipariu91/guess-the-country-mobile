---
name: run-app
description: Builds and installs the debug app on an Android emulator/device or an iOS simulator, launches it, and saves a screenshot to read. Use to visually verify UI changes, or when the user asks to run or screenshot the app.
argument-hint: "<android|ios> [seconds to wait, default 5] [screenshot label]"
allowed-tools: Bash(bash ${CLAUDE_SKILL_DIR}/scripts/run.sh *)
---

Run:

```bash
bash ${CLAUDE_SKILL_DIR}/scripts/run.sh $ARGUMENTS
```

The first argument is `android` or `ios`. The script prints the screenshot path on its last line. Read that
image and describe what's on screen in relation to the change being verified.
Pass a label as the third argument to name the file, for example `ios 5 before-empty-state`. The script
adds the platform to the file name, so both platforms can use the same label.

**Android:** if the script says no device is connected, tell the user and list the AVDs it printed. Offer to
start one in the background with `$HOME/Library/Android/sdk/emulator/emulator -avd <name>`, and don't
start it without their OK. The script opens `MainActivity`. Set `ANDROID_ACTIVITY=CatalogueActivity` to open
the debug design catalogue instead, for example to check a design-system component.

**iOS:** the script reuses a booted iPhone simulator, or boots the newest available one. Set `IOS_SIM` to a
simulator name or UDID to pick one. The first build links the Kotlin framework and takes a few minutes.
Run it in the background if needed.

If the script says the app is not running after launch, it prints the crash (Android `logcat -b crash`,
iOS console log). The error `Room cannot verify the data integrity` means an older install has a different
schema. Room stays at version 1 pre-release, so uninstall and run again:
`adb uninstall <applicationId>` or `xcrun simctl uninstall booted <bundle id>`. This wipes the app's
local data on that device, so tell the user. Any other crash is a bug: report it, don't screenshot around it.

After a fresh install, Google's test consent form (UMP) can cover the app on first launch. Say so, and ask
the user before answering it for them.

Dark mode:
- Android: `adb shell cmd uimode night yes` before the script, `adb shell cmd uimode night no` afterwards.
- iOS: `xcrun simctl ui booted appearance dark` before, `xcrun simctl ui booted appearance light` afterwards.
