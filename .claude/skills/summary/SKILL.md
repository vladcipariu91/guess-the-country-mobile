---
name: summary
description: Produces a short, visual HTML summary of a finished change - screenshots (before/after for UI bugs, new screens for UI features, Android and iOS side by side), where it sits in the architecture, what changed, checks run, and how to try it. Use after a feature or fix is implemented and verified, before handing it to the user to test.
argument-hint: "[what to summarise, defaults to the current change]"
allowed-tools: Bash(python3 -I ${CLAUDE_SKILL_DIR}/scripts/render.py *) Bash(open *)
---

# Change summary

The reader should understand the change in about 30 seconds. Use short phrases, not paragraphs.
The renderer refuses text that runs too long.

## 1. Screenshots
- **UI bug:** you need a *before* and an *after*. Take the before with the `run-app` skill **before
  changing any code**. If the code has already changed, `git stash`, take it, then `git stash pop`.
- **UI feature:** take the new screens. Add a before only if an existing screen changed.
- **Non-UI change:** screenshots are optional.
- Take each screen on both platforms when the change is visible on both: `run.sh android 5 after-x` and
  `run.sh ios 5 after-x` give `build/claude/after-x-android.png` and `build/claude/after-x-ios.png`.
- Use dark mode (see `run-app`) when the change touches colours or theming.

## 2. Manifest
Write `summaries/summary.json`:

```json
{
  "type": "feature | bug | change",
  "title": "Short title of the change",
  "summary": "One sentence on what the user can now do.",
  "why": "One sentence on what was wrong or missing.",
  "layers": [
    {"name": "UI", "note": "new component in the screen"},
    {"name": "ViewModel", "note": "new field in the UI state"}
  ],
  "screens": [
    {
      "caption": "Main menu",
      "before": {"android": "build/claude/before-x-android.png", "ios": "build/claude/before-x-ios.png"},
      "after": {"android": "build/claude/after-x-android.png", "ios": "build/claude/after-x-ios.png"}
    }
  ],
  "changes": ["`SomeScreen.kt`: what changed"],
  "checks": ["N unit tests pass (JVM)", "Reviewer: no issues", "Checked on emulator + simulator"],
  "try": ["Open the app", "Do the thing", "See the result"]
}
```

Rules:
- `layers` names must come from `UI`, `ViewModel`, `Domain`, `Data`, `Platform`. List only the layers you touched.
  `Platform` covers androidMain, iosMain, the `ads` module, `androidApp/` and `iosApp/` Swift.
- `before`/`after` is either one path or `{"android": …, "ios": …}`. Include only the platforms you captured.
- Each list has at most 6 items. Each item is a phrase, not a sentence chain.
- `checks` only lists things you actually ran this session. Don't claim anything you haven't seen.

## 3. Render and show
```bash
python3 -I ${CLAUDE_SKILL_DIR}/scripts/render.py summaries/summary.json
```

If it reports text that's too long, shorten that text and run it again. When it succeeds, it prints the path
of a self-contained page under `summaries/`. Show the page to the user: render it in the side panel
if you can send files, otherwise run `open <path>`. Then reply in at most two lines and hand over for testing.
