# Passport v2 — design system and new features

Date: 2026-10-23
Design source: Claude Design canvas "Guess the Country — Design Explorations", page **Passport v2**
(https://claude.ai/artifact/N1pRufVJV9KQC36XpK8ayF). Pages "Explorations" (A/B/C) are not in scope.

## Intent

The app currently runs on Material3 defaults with `dp`/`sp`/colour values typed inline in each screen.
The Passport v2 design turns the game into a travel passport: you pick a passport colour, correct answers
get stamped, and your passport fills up with countries over time.

Two goals, in order:

1. **A design system with no dangling values.** Every colour, font, text style, spacing, size, radius,
   border and motion value comes from one place. Screens only reference named tokens and shared components.
2. **Build Passport v2 on top of it**: restyle the existing screens and add the new features
   (splash, passport picker, stamps, results visa page, passport collection, two milestone stamps).

## Goals

- `tokens.json` in the repo is the **single source of truth** for design tokens.
- A **Gradle task generates the Compose theme** (Kotlin) from `tokens.json`. Generated code is not
  hand-edited.
- A **Claude Design design-system project** uses the same `tokens.json`, so future canvases match the code.
- **System dark mode is supported**: a dimmed paper colour and a second set of paper/ink tokens.
- Every screen in the app matches the Passport v2 canvas (or the newly drawn screens, see Phase 2).

## Non-goals (this release)

- 7-day streak stamp and premium "Unlimited visa" stamp (later; premium/payments don't exist yet).
- Sound of any kind.
- Location permission or any region lookup (the passport picker suggests nothing).
- Real payments, real ads (unchanged placeholders).
- The A/B/C exploration directions.

## Anti-goals (would make this a failure even if it works)

- Raw colour hexes, `dp`/`sp` literals or font references in screen code outside the theme/component layer.
- The Kotlin theme and the Claude Design tokens drifting apart.
- Stamp animations that block play or get tiresome (they must be short and never gate the "Next" button).
- Changes to the game rules (scoring, 7 rounds, 3 free games/day, ad credits) — this is a visual and
  collection layer on top.

## Constraints

- Kotlin Multiplatform, Compose Multiplatform (Android + iOS), Room KMP, Koin — as today.
- Room DB stays at **version 1** pre-release: change the schema in place, no migrations; reinstall the app
  after schema changes.
- Flags remain emoji built from ISO codes (no image assets).
- Fonts: **Bricolage Grotesque** (display/body, weights 400/600/800) and **IBM Plex Mono** (labels, 500).
  Both are SIL OFL; bundle them in the app (Compose resources). No network font loading.
- Accessibility: text contrast ≥ 4.5:1 (3:1 for ≥ 24pt), touch targets ≥ 44pt, honour the OS
  reduce-motion setting.

---

## Part 1 — Design system

### Where it lives

- `tokens.json` at a stable path in the repo (builder's call, e.g. `mobile-app/design/tokens.json`).
- A Gradle task (in the build logic, wired so it runs before `shared` compiles) reads it and generates
  Kotlin into a generated source dir of `shared/commonMain`. CI/build fails if `tokens.json` is invalid.
- The same file is published to a new Claude Design **design-system project** ("Guess the Country —
  Passport") with preview cards for colours, type, spacing, stamps and the main components. The project's
  preview cards are pictures built from the tokens, not shared code. When `tokens.json` changes, the
  design-system project is republished.
- **Who does it** *(my call)*: Claude creates and republishes the design-system project and draws the
  Part 2 canvas page through the user's claude.ai account; the user approves on the canvas.
- **Drift guard** *(my call)*: a checked-in hash of the `tokens.json` last published to Claude Design.
  `./gradlew check` fails when the file's hash differs, with a message to republish and update the hash.
  It does not block `assemble`, so local iteration stays fast. (There's no CI yet; "build fails" means the
  local Gradle build.)

### Tokens

**Colour** — named by role, never by hue in screen code.

- *Passport covers* — the four player-selectable themes:
  burgundy `#5E1A26`, navy `#16213D`, green `#173A2C`, black `#161616`.
  The selected cover is the background of every "cover" screen (menu, rounds, results header, splash) and
  the fill of primary buttons on paper.
- *Gold accent*: `#D4A85A`, light gold `#E8C27E` (text/icons on covers).
- *Paper* (cream) `#F4EFE3`, *ink* `#1B1B1B`, *muted ink* `#3D3A33`/`#5C574B`, *label ink* `#6B5A2E`,
  *rule* `#CFC5AD`, *dashed outline* `#9C8A5C`, placeholder greys `#A89C80`/`#8A7F66`.
- *On-cover text*: `#F4EFE3` with alpha variants used in the design (0.6/0.8/0.85, option fills at 0.1/0.16).
- *Stamp inks* by tier: 5 `#7A5A12` (gold), 4 `#1F4E79` (blue), 3 `#2F6B45` (green), 2 `#5B3E8A`
  (purple), 1 `#3D3A33` (ink black), missed `#9B2C2C` (red); used-entry red `#C96A6A`/`#E8A0A0`.
- **Dark mode**: a second set for paper, ink, muted ink, rule and **lighter stamp inks** (multiply blending
  vanishes on dark paper, so stamps draw as a plain overlay in dark mode). Values are designed in Phase 2
  on the canvas, then written into `tokens.json`.
- Every text/background pair used must pass contrast on **all four covers in both modes**; where gold on a
  cover fails, adjust the token (not the screen).

**Typography** — a fixed scale of roughly 8 named styles taken from the design, e.g. score display (64,
800), screen title (30/26, 800), card title (22, 800), button (17, 700), body (15–16), small body (13–14),
mono label (11–13, letter-spaced, uppercase), mono micro (9–10). Exact list and names: builder's call,
as long as every text in the canvas maps to one.

**Spacing / size** — the steps the canvas uses (≈ 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 24, 32, 44, 48),
touch heights 44 / 48 / 56, stamp sizes, passport cover size, globe size.

**Radius** — 4, 6, 8, 10, 12, 14, 16, full.
**Border** — widths 1, 1.5, 2, 2.5, 3, 4, 5; styles solid, dashed, double.
**Elevation** — the passport cover's shadow only.
**Motion** — see below.
**Iconography** — thin line icons (stroke ~1–2) drawn as vectors; no emoji except flags.

### Motion

- **Stamp slam**: the stamp drops from ~1.4× scale to 1× with a small overshoot, ~250 ms total, landing
  at its resting tilt. A **haptic** fires at impact. No sound.
- Correct and missed answers use the same slam; missed uses a duller/"reject" haptic. *(my call)*
- "Next country" is tappable immediately; the animation never blocks input.
- Short by design: it must not get tiresome over many games.
- **Reduce motion on**: stamps appear in place, no scale; globe doesn't spin; loading bar is static.
- Splash: globe strip scrolls continuously (~14 s per revolution), loading bar animates.
- Durations/easings are tokens.

### Components (Compose, in a `designsystem`/`ui.components` package)

Primary button (on cover: gold fill; on paper: cover fill), secondary/outline button, text link button,
top bar ("‹ Menu" + mono round/score label), clue card (numbered clues + dashed rules), answer option
(states: default, disabled, selected-correct, your-wrong-guess, crossed-out/eliminated, dimmed), mode card
(with tilted points tag), free-entry slot (free / used-with-date stamp), passport cover (picker + small),
passport swatch row (picker), filter chip, progress bar, stamps (round single-ring, round double-ring
"first clue", rectangle "entry", octagon, oval "capital", double-border "missed", rectangle "cleared",
postmark, pill "first visit"), collection tile (stamped / not yet).

### Catalogue

A debug-only screen in the app listing every token and component in both modes and all four covers.

### Enforcing "no dangling values"

A check (Gradle task or test, builder's choice of mechanism) that fails the build when screen code outside
the theme/components packages contains colour literals (`Color(0x…)`), `dp`/`sp` numeric literals or font
references. *(my call — it's the cheapest way to keep the goal true over time.)*

---

## Part 2 — Canvas additions (before any screen code)

Draw on the existing canvas, on a new page (e.g. "Passport v2 — states"), using the new design system:

- Hint round **in progress**: "Another clue (−1)", "Show capital (1 pt)", "Worth N points", and the
  "Last chance" capital phase.
- **Menu with no free entries left**: "Watch an ad to play" for each mode + Upgrade.
- **Ad loading** screen.
- **Quit confirmation** dialog.
- **Error** screen.
- **Dark-mode versions** of the paper screens: choose passport, clue card / rounds, results visa page,
  my passport collection, plus the stamp set on dark paper.
- States no one has drawn yet *(added after the builder check; my call: everything undrawn goes through
  this gate)*:
  - flag round **correct** (CLEARED stamp);
  - hint round **missed** in the 2-column grid (where "CORRECT ANSWER" / "YOUR GUESS" go);
  - **results for a Guess the flag game** (small CLEARED/MISSED stamps, flag-mode header, no
    "new countries" line);
  - **results with milestones** — a new player's first game can earn up to 7 first visits plus a
    postmark. The visa page **scrolls vertically** (it can grow beyond one screen), and first visits in one
    game are drawn as **one combined "FIRST VISIT" stamp listing the new countries**, not one pill each;
  - collection with **6 chips** (Oceania) and the **empty collection**;
  - **Moments** section;
  - **change passport** from My passport (picker with a back/cancel; cancel keeps the current colour).

The user approves these on the canvas. Approved values (dark-mode colours etc.) go into `tokens.json`.
**No screen code is written until this approval.** (Design system Kotlin plumbing — generation, fonts,
light-mode tokens — may proceed in parallel.)

---

## Part 3 — Restyle existing screens

Menu, hint round, flag round, round result, game over/results, ad screen, error, quit dialog — all moved
onto the design system and matched to the canvas. Answer options become the 2-column grid with flags in
hint rounds (as P-Correct) and a single column without flags in flag rounds (as P-Denied).

## Part 4 — New features

### Splash

- In-app Compose splash: spinning globe in the selected cover (burgundy before a passport is chosen),
  "PASSPORT / Guess the Country", loading bar. Shown while the database seeds/loads.
- *(my call)* If loading finishes in under ~1 s, the splash is skipped/shortened so returning players don't
  wait for nothing. Native OS launch screen just shows the cover colour to avoid a flash.

### Choose your passport

- Shown on **first launch only**, after the splash.
- **No countries anywhere on the picker** (user's decision): the colours are presented as inspired by real
  passports, not tied to any country. Each swatch shows only its colour name — the canvas's "EU countries",
  "US, UK, Canada, Australia" etc. subtitles are dropped, and so is the "Suggested for <Country>" line
  (no region lookup at all). Navy is preselected. *(dropping the suggestion line and the navy default:
  my reading of the user's answer)*
- Pick a swatch → preview cover updates → "Issue my passport" saves the choice.
- The choice is persisted and re-themes the whole app. *(my call)* It can be changed later from
  "My passport".

### Menu

As P-Menu: emblem + title; **today's free entries** as 3 slots, used ones carry a tilted red
"USED dd.MM" stamp; the two mode cards with points tags; "My passport · N/21 stamps"; "Upgrade for
unlimited entries" link. When no free entries or ad credits are left, the menu shows the "watch an ad"
state designed in Part 2. Premium players don't see the slots. *(my call)*

### Rounds

- **Correct** (hint round): stamp slams onto the clue card. Tier depends on points: 5 first-clue
  (double ring, gold, "★ FIRST CLUE ★"), 4 admitted (blue round), 3 entry (green rectangle), 2 octagon
  (purple), 1 capital (ink oval, "VIA THE CAPITAL"). Ring text includes round number and date.
  Below: "Stamp added to your passport" (only when it's a new best for that country — otherwise a neutral
  line; *my call*), then "Next country".
- **Missed**: red double-border "MISSED · 0 PTS · NEXT TIME" stamp; correct answer outlined with
  "CORRECT ANSWER", your guess struck through with "YOUR GUESS".
- **Flag round correct**: small blue rectangle "FLAG CHECK / CLEARED / <flag> · +1" stamp.

### Results — today's visa page

As P-Results: score "N / max points" on the cover; a paper "VISAS" page with every stamp from this game
scattered with tilts (layout: builder's call — deterministic per game so it doesn't jump on recomposition,
no overlaps that hide a country name); "N new countries for your passport"; milestone stamps earned this
game also slam on here; "Main menu" button.

### My passport (collection)

- Header on the cover: "N of 21 countries stamped" + progress bar; "‹ Menu".
- Region filter chips: All, Europe, Asia, Africa, Americas, **Oceania** *(Oceania added — Australia and New
  Zealand need a home; my call)*. Chip row scrolls horizontally if needed.
- Tier colour key.
- 3-column grid: stamped countries show flag + best "+N" in their tier ink, slightly rotated; others show
  a dashed "?" circle and "Not yet".
- **Only Guess the country rounds fill the collection**, storing the **best-ever tier** per country.
  Flag rounds never affect it.
- A small **"Moments"** section at the bottom *(my call on placement and shape)*: one stamp per milestone
  type with a count and latest date (e.g. "FIRST VISITS · 12", "DAILY POSTMARKS · 5 · LAST 22 OCT"), not
  an ever-growing list. First-visit dates come from the per-country collection record; postmarks are one
  row per day.
- Entry point to change passport colour. *(my call)*

### Milestone stamps (this release)

- **First visit**: earned the first time a country is stamped in the collection. Shown on that game's
  results page (green pill "FIRST VISIT / <COUNTRY>"). Listed in Moments.
- **Daily postmark**: earned the day the player uses all 3 free games (postmark "22 OCT · 3/3"). Shown on
  the results page of the game that used the last free entry. One per day. Listed in Moments.
- First visits in one game share **one combined stamp** on the results page (see Part 2).

### When things are saved (user's decision)

- Each country is saved to the collection **the moment its stamp slams** (per finished hint round), so
  quitting a game keeps the stamps already earned. First visits are therefore detected per round too.
- The daily postmark is earned when the **3rd free game of the day starts** (the entry is used), even if
  that game is then quit. If the game finishes, the postmark slams on its results page; if it's quit, it
  simply appears in Moments.
- The results page reads what this game added (stamps, first visits, postmark), so the game's result
  state must carry more than the score.
- Streak and premium visa: **not now**.

### Data changes (Room v1, in place)

- `countries` gains a **region** (Europe/Asia/Africa/Americas/Oceania), set in the seed for all 21.
- New per-country collection record: best tier (1–5), first-stamped date.
- Passport cover choice (nullable until chosen → triggers the picker).
- Earned milestones (type + date + optional country).
- Exact table shapes: builder's call.

---

## Phasing

1. Design system plumbing: `tokens.json` (light mode), generator, fonts, theme, components, catalogue,
   enforcement check; publish the Claude Design design-system project.
2. Canvas additions (Part 2) → **user approval** → dark tokens into `tokens.json`.
3. Restyle existing screens.
4. New features: splash → picker → stamps in rounds → results visa page → collection + regions →
   first visit + daily postmark.

## Testing

- Existing domain/view-model/Room tests stay green.
- New unit tests: points → stamp tier mapping; collection keeps best-ever tier and only from hint rounds;
  first-visit detection; daily postmark on the 3rd free game only, once per day;
  token generator (valid file → expected Kotlin; invalid → build error).
- Manual check on Android and iOS simulator in light and dark mode, all four covers, reduce-motion on.

## Left to the builder

Token file format and naming, generator implementation, package layout, exact type-scale names, stamp
layout algorithm on the results page, table shapes, haptic API per platform, how the catalogue is reached
in debug builds.
