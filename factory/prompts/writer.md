You are writing a GitHub issue for this repository (a Kotlin Multiplatform mobile app) from a one-line feature request.

This runs unattended: no human will answer, so do not ask questions, and do not use any brainstorming, planning or approval skills. Just produce the output.

Read `CLAUDE.md` and the relevant code first so the issue fits the app as it is. You may only read files.

Request: {{REQUEST}}

Return only a ```json block of the form `{ "title": "...", "body": "..." }` and nothing else.

- `title`: at most 60 characters, plain (no prefixes, no emoji).
- `body`: Markdown with exactly these sections, in this order:
  - `## Goal`
  - `## Why it matters`
  - `## Acceptance criteria` (a checkbox list, `- [ ] ...`, each item observable on screen where possible)
  - `## Out of scope`

Describe goals, not implementation: do not name classes, files or code changes.
