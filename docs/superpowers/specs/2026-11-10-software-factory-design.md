# Software Factory — design

Date: 2026-11-10 · Demo: 2026-11-11 (a talk to a few friends)
Repo: `vladcipariu91/guess-the-country-mobile` (private, default branch `main`)

## Intent

Show that a very short prompt is enough when the AI is plugged into the whole project. Vlad types a
one-liner on a dashboard; the AI turns it into a well-written GitHub issue, plans it, reviews the plan,
waits for Vlad's approval, writes the code, opens a pull request linked to the issue and reviews it.
The dashboard shows each ticket travelling along that "conveyor belt".

The line the audience should leave with: *the prompt gets small because the context gets big.*

Success for tomorrow:
- Typing a sentence produces a real, well-written GitHub issue on screen within about a minute.
- At least one ticket has gone all the way through tonight (plan, review, approval, PR, code review),
  and its feature runs in the app so it can be shown.
- A second ticket is started live at the beginning of the talk and checked at the end.

## Goals

1. A local web dashboard (layout "A — conveyor belt", see mockup below) showing the repo's
   factory issues as cards in stage columns, refreshing on its own.
2. Ticket creation from a one-line prompt, written up by Claude using the repo's code as context.
3. A label-driven AI pipeline, run on Vlad's Mac: plan → plan review → human approval → build → PR →
   code review.
4. Every pipeline stage leaves a visible trace on the issue or PR (comments), so the issue reads as
   the story of the feature.
5. PRs link to their issue with `Closes #N`; merging closes the issue and the card lands in Done.

## Non-goals (for now)

- Sentry, telemetry (PostHog later) and Figma. They appear only as greyed-out "coming soon" tiles.
- The every-15-minutes AI refresh and Sentry analysis — that comes with Sentry.
- GitHub Projects board syncing. Stages live in labels (see below). A Projects board can be added later
  purely as a view on GitHub.
- Auto-merge. Vlad merges.
- Running the AI steps on GitHub Actions (later; same prompts).
- Hosting the dashboard anywhere other than Vlad's laptop; multi-user; login.

## Anti-goals (would make it a failure even if it "works")

- Anything that writes to `main` without Vlad merging a PR.
- Secrets in the repo, in the dashboard's page source, or in logs.
- The live demo depending on a long silent wait with nothing on screen.
- Touching Vlad's in-progress work on the local `passport-v2` branch (uncommitted changes).

## How it works

### Stages and labels

A ticket's column comes from exactly one `stage:*` label on its issue. Every AI step and every
dashboard button just swaps that label. Only issues with a `stage:*` label (or closed issues that had
`factory` label) appear on the board; every factory issue also carries a `factory` label.

| Column        | Label                     | Who moves it on                                   |
|---------------|---------------------------|---------------------------------------------------|
| To do         | `stage:todo`              | Vlad clicks **Plan ▸** on the card                |
| Planning      | `stage:planning`          | AI (planner, then plan reviewer)                  |
| Awaiting you  | `stage:awaiting-approval` | Vlad clicks **Approve** (or **Send back**)        |
| Building      | `stage:building`          | AI (builder, then code reviewer)                  |
| In review     | `stage:in-review`         | Vlad merges the PR on GitHub                      |
| Done          | issue closed              | GitHub, via `Closes #N`                           |

A failed AI run adds a `factory:blocked` label (the `stage:*` label stays) plus a comment saying what
went wrong; the card shows red in its column with a **Retry** button that re-runs that stage.

### Who does what — everything runs on Vlad's Mac

All AI steps run as headless Claude Code (`claude -p`) on Vlad's laptop, started by the dashboard's
server, using Vlad's existing Claude login. GitHub holds the state (issues, labels, comments, PRs);
the laptop does the work. *(Vlad's decision, 2026-11-10: fewer setup steps tonight, the iOS check
keeps working, no CI minutes. Moving the steps to GitHub Actions later reuses the same prompts.)*

1. **Writer.** Runs in the dashboard's clean checkout of `main` with read-only tools. Returns a title
   and a goal-focused body: goal, why it matters, acceptance criteria ("how we'll know it's done"),
   out of scope. The server creates the issue with labels `factory` and `stage:todo`. While it works
   the card shows a "writing…" placeholder in To do.
2. **Planner + plan reviewer** (started when Vlad clicks **Plan ▸**). The planner writes a plan
   (approach, files to touch, tests to add, risks); the server posts it as an issue comment. A second
   Claude reviews it against `CLAUDE.md` conventions; the server posts the review. If it asks for
   changes, the planner revises once and the reviewer re-checks once. Ends with
   `stage:awaiting-approval`.
3. **Vlad approves — dashboard.** **Approve** sets `stage:building` and starts the build; **Send back**
   (optional note) posts the note and runs planning again.
4. **Builder + code reviewer.** The builder implements the approved plan on branch `factory/issue-<N>`
   and commits; the repo's Stop hook makes it finish with passing tests (JVM tests, Android and iOS
   compile — this is a Mac). The server pushes the branch and opens the PR with the builder's
   description (what changed and why, how it was tested) plus `Closes #N`. A second Claude reviews the
   diff; the server posts the review on the PR. Ends with `stage:in-review`.

Each ticket's AI work happens in its own git worktree next to the checkout
(`guess-the-country/factory-runs/issue-<N>`), so `passport-v2` is never touched. One AI step runs at a
time; others queue, so the laptop stays usable for presenting. Claude writes its outputs to files in
`.factory/` (git-ignored) and the server posts them — Claude itself never needs GitHub access.

### Dashboard

- Local only: a small Node server plus a plain HTML/CSS/JS page, no build step (fewest moving parts the
  night before a talk). The server holds the GitHub token, calls the GitHub API and runs the AI steps;
  the browser never sees the token.
- Layout A: prompt box at the top ("Describe a feature in one sentence…" + **Create ticket**); six
  columns; greyed Sentry and Telemetry tiles marked "coming soon" underneath.
- Cards show number, title, and a status chip (e.g. "AI reviewing plan…", "AI coding… 6 min",
  "PR #31 ready"), from the server's own record of the running step, with elapsed time.
- Clicking a card opens a side panel with the issue's story: body, plan comment, plan review, the PR link
  and the code review — each linking out to GitHub.
- Refreshes every ~5 seconds from a server-side cache; cards animate when they change column.
- **Demo mode** (`DEMO=1`): serves a recorded snapshot of the board and ticket stories from a fixtures
  file, so the talk still works with bad Wi-Fi. Prompt box in demo mode adds a canned card.
- If the server restarts mid-step, that step is lost; the card shows "Needs attention" and Retry re-runs it.

Mockup reference: `.superpowers/brainstorm/*/content/dashboard-layout.html` (option A) in the parent
folder `guess-the-country/`.

### Where things live

- `factory/prompts/` — the writer, planner, plan-reviewer, builder and code-reviewer prompts as
  Markdown, so they're readable in the talk and editable on their own.
- `factory/dashboard/` — the Node dashboard and runner, with `.env.example` and a README with one
  command to start it.
- `.factory/` added to `.gitignore`.
- It all arrives on `main` through a normal PR that Vlad merges.

### Constraints for the AI steps

- Headless runs load the repo's `CLAUDE.md`, `.claude/settings.json` (permissions and hooks), skills and
  agents, plus Vlad's own `~/.claude` settings, but can't answer permission prompts: anything in `ask`
  or not allowed is denied. Each step passes an explicit allowed-tools list. Pushing (an `ask` rule) is
  done by the server, not by Claude.
- The builder prompt points at the `/feature` workflow and the `reviewer` agent but tells it to skip
  `/run-app` and `/summary` (keeps runs short and the simulator free for the talk).
- Each step has a turn limit and a timeout (~30 min) so a stuck run fails visibly instead of hanging.
- No change to `verify-build.sh` is needed (it runs on macOS).

### What Vlad does themself (needs their accounts)

- Create a fine-grained GitHub token (this repo only: Issues read/write, Pull requests read/write,
  Contents read/write) and put it in `factory/dashboard/.env`. The server uses it for the API and to push
  `factory/*` branches.
- Merge the `passport-v2` PR and the factory PR.

## Decisions from Vlad

- **Base branch: merge `passport-v2` into `main` first.** `origin/main` is at "Add rewarded ads" and lacks
  the 26 local `passport-v2` commits (passport, `CLAUDE.md`, `.claude/` harness, `build-logic`, `design`).
  Order of work:
  1. Check that the committed tip of `passport-v2` builds and passes the JVM tests *without* Vlad's three
     uncommitted files (`GameViewModel.kt`, `GameViewModelTest.kt`, `GoogleAdService.kt`), using a
     separate worktree. If it doesn't, stop and tell Vlad.
  2. Push `passport-v2` and open a PR into `main`; Vlad merges it. The uncommitted files stay local and
     are not part of it.
  3. The factory PR (prompts, dashboard and runner) branches from the
     updated `main`; Vlad merges it.
  4. Vlad's setup steps, then tonight's end-to-end ticket.

  Demo tickets should avoid `GameViewModel` so factory PRs don't collide with Vlad's in-progress work.
- **Vlad is free now**, so the work goes in the normal order above, and tonight's end-to-end ticket can
  be a medium-sized, clearly visible feature.
- **The talk is 15–20 minutes.** The live ticket is started at the beginning and is expected to reach
  "Awaiting you" or early "Building" by the end — approving it live is the closing beat. Tonight's
  finished ticket carries the "here's the PR and the running feature" part. Default turn limits and
  ~30-minute timeouts are fine. Building on the laptop during the talk will be noisy and slow; that's accepted.

## Left to the builder

- Exact prompts' wording, comment formats, chip wording, colours and animation.
- Picking 3–4 small, visible demo features (by reading the app) and preparing tonight's end-to-end run.
