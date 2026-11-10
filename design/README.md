# Design tokens

`tokens.json` is the single source of truth for the app's design system: colours, type, spacing, sizes, radii, borders, motion, dash, tilt and opacity.

- **App:** `./gradlew :shared:generateDesignTokens` (runs automatically before `:shared` compiles) generates the Kotlin token objects into `shared/build/generated/designTokens/`. The Compose theme and components in `shared/.../designsystem/` read them. Never hand-edit the generated code.
- **Claude Design:** the same tokens are published to the design-system project **Guess the Country — Passport**: https://claude.ai/artifact/SEycZa13tw57SfrZ4GoQWy (private; share it from the page's Share menu). That page is read-only (`"editing": "code"`), so make every token change here.

## Changing a token

1. Edit `design/tokens.json`. Every colour needs a `usage` sentence; the export fails without one.
2. Build and run the app: `./gradlew :shared:testAndroidHostTest` includes the contrast test on all four covers.
3. Export for Claude Design: `./gradlew :shared:exportDesignSystem` writes `shared/build/designSystem/project/` (`tokens.json`, `tokens.css`, `fonts/`).
4. Republish those files to the Claude Design project. Ask Claude Code to "republish the design system tokens", or copy them in yourself. Only `tokens.json`, `tokens.css` and `fonts/` are generated. The project's brand book (`README.md`), component previews, cover and index were written by hand and live only in Claude Design. Update them there when the design changes.
5. Record what was published:

   ```bash
   shasum -a 256 design/tokens.json > design/tokens.published.sha256
   ```

## Checks (`./gradlew check`)

- **`:shared:checkTokensPublished`** fails when `tokens.json` changed since its last publish to Claude Design, so the two never drift apart. The guard hashes `design/tokens.json` only: a change to the exporter (`DesignSystemExport`) also needs a republish even though the check stays green.
- **`:shared:checkNoRawDesignValues`** fails when screen code outside `designsystem/` uses raw colours (`Color(0x…)`), `dp`/`sp` number literals, `Font(`/`FontFamily(`, or `MaterialTheme.colorScheme`/`.typography`.
  - `raw-values-baseline.txt` lists screen files still allowed to break this rule until they are restyled.
  - The check also fails when a listed file no longer needs its entry, so the baseline only ever shrinks.

## Catalogue

Debug builds of the Android app have a second launcher icon, **GTC Catalogue**. It shows every token and component on all four covers in light and dark.
