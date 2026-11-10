#!/bin/bash
# Build + install the debug app, launch it, and save a screenshot. Prints the screenshot path last.
# Usage: run.sh <android|ios> [wait-seconds] [label]   (label names the screenshot, e.g. before-empty-state)
# Android: set ANDROID_ACTIVITY (default MainActivity), e.g. CatalogueActivity for the design catalogue.
# iOS: set IOS_SIM to a simulator name or UDID; otherwise a booted iPhone is reused or the newest one booted.
set -euo pipefail

root="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}"
platform="${1:-}"
wait_s="${2:-5}"
label="${3:-screenshot-$(date +%H%M%S)}"
out="$root/build/claude/$label-$platform.png"
cd "$root"

run_android() {
  local sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
  local adb="$sdk/platform-tools/adb"
  if ! "$adb" get-state >/dev/null 2>&1; then
    echo "No device or emulator connected." >&2
    echo "Available AVDs:" >&2
    "$sdk/emulator/emulator" -list-avds >&2 || true
    exit 1
  fi

  local app_id namespace
  app_id=$(sed -nE 's/.*applicationId = "([^"]+)".*/\1/p' androidApp/build.gradle.kts)
  namespace=$(sed -nE 's/.*namespace = "([^"]+)".*/\1/p' androidApp/build.gradle.kts)

  ./gradlew :androidApp:installDebug --console=plain -q
  "$adb" shell am force-stop "$app_id"
  # Debug builds have two launcher activities (the app and the design catalogue), so name one explicitly.
  "$adb" shell am start -W -n "$app_id/$namespace.${ANDROID_ACTIVITY:-MainActivity}" >/dev/null
  sleep "$wait_s"

  if ! "$adb" shell pidof "$app_id" >/dev/null; then
    echo "The app is not running after launch (crashed?). Last crash:" >&2
    "$adb" logcat -d -b crash | tail -20 >&2
    exit 1
  fi

  mkdir -p "$(dirname "$out")"
  "$adb" exec-out screencap -p >"$out"
}

pick_simulator() {
  local devices
  devices=$(xcrun simctl list devices available -j)
  if [[ -n "${IOS_SIM:-}" ]]; then
    jq -r --arg s "$IOS_SIM" '[.devices[][] | select(.udid == $s or .name == $s)][0].udid // empty' <<<"$devices"
    return
  fi
  local booted
  booted=$(jq -r '[.devices[][] | select(.state == "Booted" and (.name | startswith("iPhone")))][0].udid // empty' <<<"$devices")
  if [[ -n "$booted" ]]; then
    echo "$booted"
    return
  fi
  # Newest iOS runtime, first iPhone in it.
  jq -r '[.devices | to_entries[] | select(.key | contains("iOS"))]
         | sort_by(.key | capture("iOS-(?<v>[0-9-]+)").v | split("-") | map(tonumber))
         | map(.value[] | select(.name | startswith("iPhone"))) | last.udid // empty' <<<"$devices"
}

run_ios() {
  local udid
  udid=$(pick_simulator)
  if [[ -z "$udid" ]]; then
    echo "No matching iPhone simulator. Available:" >&2
    xcrun simctl list devices available | grep iPhone >&2 || true
    exit 1
  fi
  xcrun simctl boot "$udid" 2>/dev/null || true # already booted is fine
  xcrun simctl bootstatus "$udid" -b >/dev/null

  local derived="$root/build/claude/ios-derived"
  local log="$root/build/claude/xcodebuild.log"
  mkdir -p "$(dirname "$log")"
  if ! xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
    -destination "id=$udid" -derivedDataPath "$derived" -quiet build >"$log" 2>&1; then
    echo "xcodebuild failed (full log: $log):" >&2
    grep -E 'error:|^e: |BUILD FAILED|What went wrong' -A2 "$log" | head -30 >&2
    exit 1
  fi

  local app
  app=$(find "$derived/Build/Products/Debug-iphonesimulator" -maxdepth 1 -name '*.app' | head -1)
  local bundle_id
  bundle_id=$(/usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' "$app/Info.plist")

  xcrun simctl install "$udid" "$app"
  xcrun simctl terminate "$udid" "$bundle_id" 2>/dev/null || true
  xcrun simctl launch "$udid" "$bundle_id" >/dev/null
  sleep "$wait_s"

  # Capture first: `| grep -q` would SIGPIPE launchctl and fail under pipefail.
  local services
  services=$(xcrun simctl spawn "$udid" launchctl list)
  if [[ "$services" != *"UIKitApplication:$bundle_id"* ]]; then
    # Relaunch with the console attached: Kotlin prints uncaught exceptions there.
    # (Killing a --console-pty launch also kills the app, so it's only used once the app is already dead.)
    local console="$root/build/claude/ios-console.log"
    xcrun simctl launch --console-pty "$udid" "$bundle_id" >"$console" 2>&1 &
    local console_pid=$!
    sleep 5
    kill "$console_pid" 2>/dev/null || true
    echo "The app is not running after launch (crashed?). Console: $console" >&2
    grep -E 'Uncaught Kotlin exception|Fatal' "$console" | head -5 >&2
    exit 1
  fi

  mkdir -p "$(dirname "$out")"
  xcrun simctl io "$udid" screenshot "$out" >/dev/null 2>&1
}

case "$platform" in
  android) run_android ;;
  ios) run_ios ;;
  *)
    echo "Usage: run.sh <android|ios> [wait-seconds] [label]" >&2
    exit 1
    ;;
esac
echo "$out"
