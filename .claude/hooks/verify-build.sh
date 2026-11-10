#!/bin/bash
# Stop hook: if Kotlin, Gradle, XML or Swift files have uncommitted changes, Claude can't finish its turn
# until common tests pass on the JVM and Android + iOS Kotlin compile. Exit 2 sends the failure back.
# Leaves out iOS simulator tests and the Swift/Xcode build (slow); run those by hand for iOS changes.
# Unchanged repos skip this entirely; repeat runs are fast because Gradle tasks stay up to date.

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}" || exit 0

# --untracked-files=all: a new file in a new package would otherwise show only as its directory.
if ! git status --porcelain --untracked-files=all | grep -qE '\.(kt|kts|xml|swift)"?$'; then
  exit 0
fi

out=$(./gradlew \
  :shared:testAndroidHostTest :ads:testAndroidHostTest \
  :androidApp:compileDebugKotlin :shared:compileKotlinIosSimulatorArm64 \
  --console=plain -q 2>&1)
status=$?
[[ $status -eq 0 ]] && exit 0

{
  echo "Build or unit tests are failing. Fix them before finishing:"
  details=$(grep -E '^e: |FAILED|AssertionError|Exception|What went wrong' -A3 <<<"$out" | head -40)
  echo "${details:-$(tail -30 <<<"$out")}"
  # Gradle's -q output doesn't name failing tests; the JUnit XML reports do.
  # Only after a test failure, so stale reports never show up next to a compile error.
  if grep -q 'There were failing tests' <<<"$out"; then
    echo "Failing tests:"
    grep -l '<failure' {shared,ads}/build/test-results/*/*.xml 2>/dev/null |
      xargs -I{} perl -0ne 'while (/<testcase name="([^"]+)" classname="([^"]+)"[^>]*>\s*<failure message="([^"]*)"/g) {
        my ($t, $c, $m) = ($1, $2, $3); $m =~ s/&lt;/</g; $m =~ s/&gt;/>/g; $m =~ s/&quot;/"/g; $m =~ s/&amp;/&/g;
        print "$c > $t: $m\n" }' {} | head -10
  fi
} >&2
exit 2
