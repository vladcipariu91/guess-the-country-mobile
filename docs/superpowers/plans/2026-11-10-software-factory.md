# Software Factory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A local "conveyor belt" dashboard plus a label-driven Claude pipeline running on Vlad's Mac that turns a one-line prompt into a planned, reviewed, approved, built and reviewed PR for `guess-the-country-mobile`, ready to demo on 2026-11-11.

**Architecture:** A dependency-free Node 24 server (`factory/dashboard/`) talks to the GitHub REST API with Vlad's token, serves a plain HTML/JS board, and runs every AI step itself as headless `claude -p` in a per-ticket git worktree. Each ticket's column is one `stage:*` label on GitHub. Claude writes its outputs to `.factory/*.md`; the server posts them as comments, pushes the branch and opens the PR.

**Tech Stack:** Node 24 (built-in `fetch`, `node:http`, `node:child_process`, `node:test`, `--env-file`), plain HTML/CSS/JS, Claude Code CLI, git worktrees, Gradle/KMP on macOS.

**Spec:** `docs/superpowers/specs/2026-11-10-software-factory-design.md`

## Global Constraints

- All work happens in git worktrees; the `passport-v2` checkout in `mobile-app/` (and its 3 uncommitted files) is never modified, stashed or switched.
- Nothing is committed on `main`; everything reaches `main` through a PR that Vlad merges. Pushing needs Vlad's go-ahead each time.
- No npm dependencies. `factory/dashboard/package.json` has no `dependencies`; tests run with `node --test`.
- The GitHub token lives only in `factory/dashboard/.env` (already git-ignored by `.env`) and in server memory; never sent to the browser or logged.
- Labels, exactly: `factory`, `stage:todo`, `stage:planning`, `stage:awaiting-approval`, `stage:building`, `stage:in-review`, `factory:blocked`. Done = issue closed.
- Columns, exactly, in order: To do · Planning · Awaiting you · Building · In review · Done.
- Builder branch: `factory/issue-<N>`; PR base `main`; PR body contains `Closes #<N>`.
- Comment markers (first line of each AI comment, added by the server): `<!-- factory:plan -->`, `<!-- factory:plan-review -->`, `<!-- factory:code-review -->`.
- Per-ticket worktree: `/Users/vladcip/Development/guess-the-country/factory-runs/issue-<N>`; Claude outputs go to `.factory/` inside it (git-ignored).
- One AI step runs at a time (FIFO queue). Each step: `claude -p` with `--output-format json`, an explicit `--allowedTools`, `--max-turns` (planner/reviewers 30, builder 80), and a 30-minute kill timeout.
- Claude never pushes and never calls GitHub; the server does both with the token.
- Demo tickets avoid `GameViewModel` (Vlad's in-progress work).

**Plan calls (deviations from the spec's wording, same intent):** failed runs add a separate `factory:blocked` label and keep the stage label, so "exactly one `stage:*` label" stays true and Retry knows the column; the branch is `factory/issue-<N>` instead of `factory/<N>-<slug>` so the dashboard can find the PR without searching.

## Review Focus

1. Double-clicking **Approve** / **Plan ▸**, or clicking while a step is going → the server refuses actions not valid from the card's current column (409), and the runner ignores a ticket that is already queued or running. Test: Task 6 `rejects an action that is not valid from the current column`.
2. The writer returns no JSON, errors, or takes over 120 s → a ticket is still created from the raw prompt, with a note in the body. Test: Task 4 `falls back to the raw prompt when Claude output is unusable`.
3. GitHub unreachable or token wrong mid-talk → the board keeps showing the last good data with a visible error banner instead of going blank. Test: Task 6 `serves the cached board with an error when GitHub fails`.
4. A build is retried after a run that already pushed `factory/issue-<N>` or opened its PR → the builder reuses the branch/PR rather than failing on "already exists"; the dashboard shows the newest PR. Test: Task 8 `retrying a build reuses the existing worktree branch and PR`.
5. Empty or whitespace prompt, or a pasted essay → 400 for empty, prompt trimmed and capped at 500 characters. Test: Task 6 `rejects an empty prompt`.

---

### Task 1: Get `passport-v2` onto `main`

**Files:** none changed. Worktree: `/Users/vladcip/Development/guess-the-country/gtc-passport-check`.

- [ ] **Step 1: Check the committed tip builds without the uncommitted files**

```bash
cd /Users/vladcip/Development/guess-the-country/mobile-app
git worktree add ../gtc-passport-check passport-v2
cd ../gtc-passport-check
cp ../mobile-app/local.properties .   # Android SDK path only
./gradlew :shared:testAndroidHostTest :ads:testAndroidHostTest :androidApp:compileDebugKotlin --console=plain
```
Expected: `BUILD SUCCESSFUL`. If it fails, stop and show Vlad the failure; do not continue.

- [ ] **Step 2: With Vlad's go-ahead, push and hand over the PR link**

Run: `git push -u origin passport-v2`
Expected: push succeeds and prints a "Create a pull request" URL. Give Vlad `https://github.com/vladcipariu91/guess-the-country-mobile/compare/main...passport-v2` and wait until Vlad says it's merged.

- [ ] **Step 3: Remove the check worktree**

Run: `git -C ../mobile-app worktree remove ../gtc-passport-check`

### Task 2: Factory branch

**Files:**
- Worktree: `/Users/vladcip/Development/guess-the-country/gtc-factory-dev` on new branch `factory-setup` from `origin/main`
- Modify: `.gitignore` (add `.factory/`)
- Copy in: `docs/superpowers/specs/2026-11-10-software-factory-design.md`, `docs/superpowers/plans/2026-11-10-software-factory.md` (untracked in the `mobile-app/` checkout)

**Interfaces:** Produces the `factory-setup` branch every later task commits to.

- [ ] **Step 1: Create the worktree**

```bash
cd /Users/vladcip/Development/guess-the-country/mobile-app
git fetch origin
git worktree add -b factory-setup ../gtc-factory-dev origin/main
cp local.properties ../gtc-factory-dev/
```

- [ ] **Step 2: Add `.factory/` to `.gitignore`, copy in the two docs.** Check: `git check-ignore .factory/plan.md` prints the path.

- [ ] **Step 3: Commit** — `git add .gitignore docs/superpowers && git commit -m "Add factory design and plan; ignore .factory/"`

### Task 3: Stages and the GitHub client

**Files:**
- Create: `factory/dashboard/package.json` (`"type": "module"`, scripts `start: node --env-file-if-exists=.env src/server.js`, `test: node --test`, `setup: node --env-file=.env src/setup-labels.js`, `snapshot: node --env-file=.env src/snapshot.js`)
- Create: `factory/dashboard/src/stages.js`, `factory/dashboard/src/github.js`
- Test: `factory/dashboard/test/stages.test.js`, `factory/dashboard/test/github.test.js`

**Interfaces:**
- Produces (`stages.js`):
  - `STAGES: Array<{ id: 'todo'|'planning'|'awaiting-approval'|'building'|'in-review'|'done', title: string, label: string|null }>` in the column order from Global Constraints.
  - `ALL_LABELS: string[]` — the seven labels.
  - `columnFor(issue: Issue): string|null` — `'done'` if closed; else the id of its `stage:*` label; else `null`.
  - `ACTIONS: { plan: {from:'todo', to:'planning'}, approve: {from:'awaiting-approval', to:'building'}, 'send-back': {from:'awaiting-approval', to:'planning'} }` plus `retry` (planning or building column, only when blocked).
- Produces (`github.js`): `createGitHubClient({ token, repo, fetchImpl = fetch })` returning:
  - `listFactoryIssues(): Promise<Issue[]>` — `Issue = { number, title, url, state: 'open'|'closed', labels: string[], closedAt: string|null }`; GET `/repos/{repo}/issues?labels=factory&state=all&per_page=100`, dropping items with `pull_request`.
  - `createPull({ head, title, body }): Promise<{ number, url }>` — POST `/repos/{repo}/pulls`, base `main`.
  - `getIssue(n): Promise<Issue & { body }>`
  - `findPullForIssue(n): Promise<{ number, url, state } | null>` — GET `/repos/{repo}/pulls?head={owner}:factory/issue-{n}&state=all`, newest `created_at` first.
  - `getComments(n): Promise<Array<{ author, body, createdAt }>>`
  - `createIssue({ title, body, labels }): Promise<{ number, url }>`
  - `setStage(n, stageLabel): Promise<void>` — PUT the issue's labels with every `stage:*` and `factory:blocked` removed and `stageLabel` added.
  - `setBlocked(n, blocked: boolean): Promise<void>` — adds or removes `factory:blocked`.
  - `addComment(n, body): Promise<void>` (works for PR numbers too)
  - `createLabel(name, color): Promise<void>` — ignores 422 (already exists).
  - All requests send `Authorization: Bearer <token>`, `Accept: application/vnd.github+json`, `X-GitHub-Api-Version: 2022-11-28`; a non-2xx throws `GitHubError` with `.status`.

- [ ] **Step 1: Write failing tests**

`stages.test.js`: `columnFor` returns `'done'` for `{state:'closed', labels:['factory','stage:in-review']}`, `'building'` for `{state:'open', labels:['factory','stage:building','factory:blocked']}`, `null` for `{state:'open', labels:['factory']}`; `STAGES.map(s => s.title)` equals `['To do','Planning','Awaiting you','Building','In review','Done']`.

`github.test.js` (with a recording `fetchImpl` stub): `listFactoryIssues drops pull requests`; `setStage replaces the old stage label and clears blocked` (PUT body labels equal `['factory','stage:building']` given current `['factory','stage:awaiting-approval','factory:blocked']`); `createPull targets main`; `findPullForIssue returns the most recent PR for the branch`; `throws GitHubError with status on 401`; `sends the bearer token header`.

- [ ] **Step 2: Run to see them fail**

Run: `cd factory/dashboard && npm test`
Expected: FAIL (modules missing).

- [ ] **Step 3: Implement `stages.js` and `github.js` with the interfaces above.**

- [ ] **Step 4: Run tests**

Run: `npm test` → Expected: all PASS.

- [ ] **Step 5: Commit** — `git add factory/dashboard && git commit -m "factory: stages and GitHub client"`

### Task 4: The writer

**Files:**
- Create: `factory/prompts/writer.md`, `factory/dashboard/src/writer.js`
- Test: `factory/dashboard/test/writer.test.js`

**Interfaces:**
- Produces: `writeTicket(prompt: string, { repoDir: string, runClaude = defaultRunClaude, timeoutMs = 120000 }): Promise<{ title: string, body: string, fallback: boolean }>`
- Produces: `parseWriterOutput(stdout: string): { title, body } | null` — `stdout` is `claude --output-format json` output; reads `.result`, takes the first ```json fenced block (or the whole text) and requires string `title` and `body`.
- `defaultRunClaude(fullPrompt, { cwd, timeoutMs }): Promise<string>` — spawns `claude -p <fullPrompt> --output-format json --allowedTools "Read,Grep,Glob"` in `cwd`; rejects on non-zero exit or timeout. Before spawning, best-effort `git -C <cwd> fetch origin && git -C <cwd> checkout --detach origin/main` (errors ignored).

`writer.md` tells Claude: you are writing a GitHub issue for this repo from a one-line request; read `CLAUDE.md` and the relevant code; return only a ```json block `{ "title": ..., "body": ... }`; title ≤ 60 characters, plain; body in Markdown with exactly these sections: `## Goal`, `## Why it matters`, `## Acceptance criteria` (checkbox list, observable on screen where possible), `## Out of scope`; describe goals, not implementation. The request is substituted at `{{REQUEST}}`.

- [ ] **Step 1: Write failing tests**: `parses title and body from a fenced json block`; `returns null when the result has no usable json`; `falls back to the raw prompt when Claude output is unusable` (stub returns `{"result":"sorry"}` → `{ title: 'add a hint button', fallback: true }`, body contains the prompt and the line `_Written without AI: the writer failed._`); `falls back when runClaude rejects` (timeout); `caps the title at 60 characters`.

- [ ] **Step 2: Run** `npm test` → Expected: FAIL.

- [ ] **Step 3: Implement `writer.js` and `writer.md`.**

- [ ] **Step 4: Run** `npm test` → Expected: PASS. Then one real smoke run from `gtc-factory-dev`: `node -e "import('./src/writer.js').then(m=>m.writeTicket('add a hint button',{repoDir:'../..'})).then(console.log)"` → a ticket with all four sections, `fallback: false`.

- [ ] **Step 5: Commit** — `git commit -am "factory: local ticket writer"` (add new files first).

### Task 5: Board model and demo data

**Files:**
- Create: `factory/dashboard/src/board.js`, `factory/dashboard/src/demo.js`, `factory/dashboard/fixtures/demo.json`, `factory/dashboard/src/snapshot.js`
- Test: `factory/dashboard/test/board.test.js`, `factory/dashboard/test/demo.test.js`

**Interfaces:**
- Consumes: `STAGES`, `columnFor` (Task 3); `Issue` shape (Task 3); runner status shape (Task 8).
- Produces: `buildBoard({ issues, status, pulls, now }): Board` where `status: Record<number, { step: 'planning'|'reviewing-plan'|'building'|'reviewing-code', state: 'queued'|'running', startedAt }>` and where `pulls: Record<number, {number,url,state}>`,
  `Board = { columns: Array<{ id, title, cards: Card[] }>, updatedAt: string }`,
  `Card = { number, title, url, column, blocked: boolean, chip: string|null, prNumber: number|null }`. Done shows the 10 most recently closed. Issues where `columnFor` is `null` are skipped.
- Chip rules: blocked → `Needs attention`; queued → `Queued`; running step → `AI planning… <m> min` / `AI reviewing plan… <m> min` / `AI coding… <m> min` / `AI reviewing code… <m> min`; planning or building with no status → `Needs attention` (step lost, e.g. server restart); awaiting-approval → `Approve plan`; in-review → `PR #<n> ready`; others `null`. Minutes = floor((now − startedAt)/60000).
- Produces: `createDemoClient(fixturePath)` with the same methods as the GitHub client; reads go to the fixture, writes mutate an in-memory copy (`createIssue` appends a To do issue, `setStage` moves it) so the board reacts during the talk.
- `demo.json` holds `{ issues, status, pulls, comments: Record<number, Comment[]> }`. Hand-write it now with 6 plausible tickets, one per column (e.g. "Daily streak counter", "Hint button that reveals the continent", "Haptic buzz on a correct guess", "Share my passport", "Zoomable flag", "Sound on/off toggle"), each with plan / plan-review / code-review comments where its column implies them.
- `snapshot.js` writes the live board's `{ issues, pulls, comments }` (plus a hand-set `status` for the Building card) into `fixtures/demo.json` (run tonight after the end-to-end ticket).

- [ ] **Step 1: Write failing tests**: `puts each issue in its stage column`; `shows elapsed minutes for a running build` (running `building` step started 6 min before `now` → `AI coding… 6 min`); `shows Needs attention when a planning card has no status`; `marks blocked cards`; `limits Done to the 10 most recent`; `skips issues without a stage`; demo: `createIssue then listFactoryIssues includes it in To do`; `setStage moves a demo issue`.
- [ ] **Step 2: Run** `npm test` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `npm test` → PASS.
- [ ] **Step 5: Commit** — `git commit -m "factory: board model, demo client and fixtures"`.

### Task 6: HTTP server

**Files:**
- Create: `factory/dashboard/src/server.js`, `factory/dashboard/src/app.js`
- Test: `factory/dashboard/test/server.test.js`

**Interfaces:**
- Consumes: GitHub/demo client (Tasks 3, 5), `buildBoard` (Task 5), `writeTicket` (Task 4), `ACTIONS` (Task 3), runner (Task 8: `enqueue(n, stage)`, `status()`).
- Produces: `createApp({ client, writeTicket, runner, demo: boolean, now = Date.now }): http.RequestListener`. `server.js` only reads env (`GITHUB_TOKEN`, `GITHUB_REPO=vladcipariu91/guess-the-country-mobile`, `FACTORY_REPO_DIR` default `../..`, `FACTORY_RUNS_DIR` default `../../../factory-runs`, `PORT=4321`, `DEMO`), builds client and runner (a no-op fake runner in demo mode that moves cards after a few seconds) and listens.
- Routes:
  - `GET /` and static files from `public/`.
  - `GET /api/board` → `Board & { demo, error: string|null }`. Cached 4 s; on client failure returns the last good board with `error` set (200), or 502 `{ error }` if there is none yet. Fetches `findPullForIssue` only for in-review cards.
  - `GET /api/issues/:n` → `{ number, title, body, url, comments: Array<{author, body, createdAt, kind}>, pr }`; `kind` from the marker (`plan`, `plan-review`, `code-review`, else `other`); code-review comments come from the PR's comments.
  - `POST /api/tickets` `{ prompt }` → trims, caps at 500 chars, 400 if empty; `writeTicket` then `createIssue` with labels `['factory','stage:todo']`; 201 `{ number, url, fallback }`. Invalidates the cache.
  - `POST /api/issues/:n/actions` `{ action, note? }` → 409 unless the issue's current column equals `ACTIONS[action].from` (retry: only when blocked); `send-back` with a note posts it as a comment first; then `setStage` (and `setBlocked(n,false)` for retry) and `runner.enqueue(n, 'planning'|'building')`; 200 `{ ok: true }`.

- [ ] **Step 1: Write failing tests** (start the listener on port 0 with stub client and writer): `GET /api/board returns six columns`; `serves the cached board with an error when GitHub fails`; `rejects an empty prompt` (400); `creates a ticket with factory and stage:todo labels`; `rejects an action that is not valid from the current column` (approve on a To do issue → 409, no `setStage` call); `send-back posts the note then moves to planning`; `approve enqueues a build`; `never includes the token in any response` (token string absent from all bodies).
- [ ] **Step 2: Run** `npm test` → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `npm test` → PASS.
- [ ] **Step 5: Commit** — `git commit -m "factory: dashboard server"`.

### Task 7: Dashboard page (layout A)

**Files:**
- Create: `factory/dashboard/public/index.html`, `public/app.js`, `public/styles.css`

**Interfaces:** Consumes the Task 6 routes only.

Layout per spec and the option-A mockup (`guess-the-country/.superpowers/brainstorm/*/content/dashboard-layout.html`): header "Software Factory · guess-the-country" (plus a `DEMO` pill when `demo`); prompt box "Describe a feature in one sentence…" + **Create ticket**; six columns; Sentry and Telemetry tiles greyed with "coming soon". Polls `/api/board` every 5 s; a card that changed column gets a short highlight; a red banner shows `error`. While a ticket is being written, a "writing…" placeholder card sits in To do. Card buttons: To do **Plan ▸**, Awaiting you **Approve** / **Send back** (prompts for an optional note), blocked **Retry**. Clicking a card opens a side panel with the story (body, plan, plan review, PR link, code review) rendered as text with line breaks; links open GitHub in a new tab. Light and dark via `prefers-color-scheme`. Readable from the back of a room: card titles ≥ 16 px.

- [ ] **Step 1: Build the page.**
- [ ] **Step 2: Verify in demo mode**: `cd factory/dashboard && DEMO=1 npm start`, open `http://localhost:4321` in the browser pane. Check: six columns titled per Global Constraints; each demo card in its column; Create ticket adds a To do card within ~2 s; Plan ▸ on it moves it to Planning on the next poll; card click shows the story; screenshot looks like mockup A.
- [ ] **Step 3: Commit** — `git commit -m "factory: conveyor-belt dashboard page"`.

### Task 8: Prompts and the local runner

**Files:**
- Create: `factory/prompts/planner.md`, `factory/prompts/plan-reviewer.md`, `factory/prompts/builder.md`, `factory/prompts/code-reviewer.md`
- Create: `factory/dashboard/src/runner.js`
- Test: `factory/dashboard/test/runner.test.js`

**Interfaces:**
- Consumes: GitHub client (Task 3), `defaultRunClaude`-style spawning from Task 4 (generalised to take `allowedTools`, `maxTurns`, `timeoutMs`).
- Produces: `createRunner({ client, repoDir, runsDir, runClaude, git, now })` returning `enqueue(n, 'planning'|'building'): boolean` (false if `n` is already queued/running) and `status(): Record<number, { step, state, startedAt }>`. `git(args, cwd)` is injectable for tests.

Prompts (the runner fills `{{ISSUE}}` with the issue title, body and comments as Markdown; each prompt starts by reading `CLAUDE.md`):
- **planner.md** — write `.factory/plan.md` with `## Approach`, `## Files`, `## Tests`, `## Risks`; change nothing else. With `{{REVIEW}}` present, write a revised plan addressing it.
- **plan-reviewer.md** — check the plan against the acceptance criteria and `CLAUDE.md`; write `.factory/plan-review.md` with a first line `Verdict: APPROVE` or `Verdict: CHANGES`, then reasons.
- **builder.md** — implement the approved plan (and any later notes from Vlad) following `/feature`, skipping `/run-app` and `/summary`; you are on branch `factory/issue-<N>`; commit your work (do not push); write `.factory/pr.md` with `## What changed`, `## Why`, `## How it was tested`.
- **code-reviewer.md** — review `git diff origin/main...HEAD`; write `.factory/code-review.md`: summary, issues ranked by severity, verdict; change nothing.

Runner behaviour:
- Worktree: `runsDir/issue-<N>`; if missing, `git fetch origin` then `git worktree add -B factory/issue-<N> <dir> origin/main` (from `repoDir`), then copy `repoDir/local.properties` into it (the Stop hook's Gradle build needs the Android SDK path); if present, reuse it as is.
- **planning**: planner (tools `Read,Glob,Grep,Write`) → post `<!-- factory:plan -->\n` + plan.md → reviewer (same tools) → post `<!-- factory:plan-review -->\n` + review → if verdict `CHANGES`: planner with review, post, reviewer again, post → `setStage(n,'stage:awaiting-approval')`.
- **building**: builder (tools `Read,Edit,Write,Glob,Grep,Task,Bash(./gradlew:*),Bash(git status:*),Bash(git diff:*),Bash(git add:*),Bash(git commit:*),Bash(git log:*)`) → fail if `git rev-list --count origin/main..HEAD` is 0 → `git push -u origin factory/issue-<N>` using the token (`https://x-access-token:<token>@github.com/<repo>.git`, never logged) → `findPullForIssue(n)` or `createPull({ head, title: issue title, body: pr.md + '\n\nCloses #<N>' })` → code reviewer (tools `Read,Glob,Grep,Bash(git diff:*),Bash(git log:*)`) → post `<!-- factory:code-review -->\n` + review on the PR → `setStage(n,'stage:in-review')`.
- Any failure (non-zero exit, timeout, missing output file): `setBlocked(n,true)` and `addComment(n, "Factory step <step> failed: <short reason>")`; the queue moves on.
- Before each Claude step, delete that step's `.factory/` output file so a stale one can't pass.

- [ ] **Step 1: Write the four prompts.**
- [ ] **Step 2: Write failing tests** (stub client, `runClaude` that writes the expected `.factory/` file into a temp worktree, recording `git`): `planning posts plan and review then awaits approval`; `a CHANGES verdict triggers exactly one revision`; `building pushes, opens a PR with Closes #N and posts the code review`; `retrying a build reuses the existing worktree branch and PR` (worktree exists, `findPullForIssue` returns PR 31 → no `createPull`, no `worktree add`); `a failed step marks the issue blocked and runs the next queued job`; `enqueue refuses a ticket already queued`; `the token never appears in comments or thrown errors`.
- [ ] **Step 3: Run** `npm test` → FAIL.
- [ ] **Step 4: Implement `runner.js`.**
- [ ] **Step 5: Run** `npm test` → PASS.
- [ ] **Step 6: Commit** — `git commit -m "factory: local AI runner and prompts"` (add new files first).

### Task 9: Setup script, README, and the factory PR

**Files:**
- Create: `factory/dashboard/src/setup-labels.js` (creates the seven labels via `createLabel`, colours: stages purple `6b5bff`, `factory` grey `8a8a8a`, `factory:blocked` red `d73a4a`), `factory/dashboard/.env.example` (`GITHUB_TOKEN=`, `GITHUB_REPO=vladcipariu91/guess-the-country-mobile`, `FACTORY_REPO_DIR=../..`, `FACTORY_RUNS_DIR=../../../factory-runs`, `PORT=4321`), `factory/README.md`.

`factory/README.md`: what the factory is (one paragraph, the conveyor belt), the label table, Vlad's one-time setup (create the fine-grained token with the permissions in the spec's "What Vlad does themself" section, put it in `.env`), and the run commands: `npm run setup`, `npm start`, `DEMO=1 npm start`, `npm run snapshot`.

- [ ] **Step 1: Write the files**; run `npm test` → PASS.
- [ ] **Step 2: Commit** — `git commit -m "factory: setup script and README"`.
- [ ] **Step 3: With Vlad's go-ahead, push and hand over the PR** — `git push -u origin factory-setup`; give Vlad `https://github.com/vladcipariu91/guess-the-country-mobile/compare/main...factory-setup`. Wait for "merged" and "token done".

### Task 10: Go live and run tonight's ticket

**Files:** `factory/dashboard/fixtures/demo.json` (refreshed by snapshot); nothing else.

- [ ] **Step 1: Writer/dashboard worktree on `main`**

```bash
cd /Users/vladcip/Development/guess-the-country/mobile-app
git fetch origin
git worktree add --detach ../factory-worktree origin/main
```
Vlad copies their token into `../factory-worktree/factory/dashboard/.env` (Claude never reads that file). Then `cd ../factory-worktree/factory/dashboard && npm run setup && npm start`.
Expected: the seven labels exist on GitHub; the board loads with no error banner.

- [ ] **Step 2: Pick the demo features** — read the app (`shared/src/commonMain/.../ui`, `domain/`) and propose to Vlad 3–4 small, visible features that don't touch `GameViewModel`; Vlad picks one for tonight and one for the live talk.

- [ ] **Step 3: End to end** — Vlad types tonight's one-liner; check the issue has the four sections; Plan ▸; watch the chips until the card is in Awaiting you with a plan and plan review on the issue; Vlad approves; watch until In review with `PR #… ready` and a code-review comment on the PR. Expected: PR body contains `Closes #N`; the issue page links the PR. If a step fails, read the server log and the worktree, fix the prompt or runner on a branch, PR, Vlad merges, restart the server from the updated worktree, Retry.

- [ ] **Step 4: Show the feature** — in `factory-runs/issue-N` run `/run-app android` (and iOS if time) to confirm the feature on screen. Vlad decides whether to merge before the talk.

- [ ] **Step 5: Refresh demo mode** — `npm run snapshot`; `DEMO=1 npm start` shows the real ticket's story. Commit `fixtures/demo.json` on a branch only if Vlad wants it in the repo.
