# Software factory

A local conveyor belt for tickets. Vlad types a prompt into the dashboard, which files a GitHub issue labelled `factory` (an issue with only the `factory` label, made by hand, doesn't show up; it needs a `stage:` label too); the dashboard on their Mac shows it
as a card and moves it along: an AI planner writes a plan, Vlad approves it, an AI builder implements it on its own
branch, the server pushes and opens a PR, and an AI reviewer comments. Vlad merges. Every AI step is a headless
`claude -p` started by the dashboard server, using the `claude` CLI's own login. One AI step runs at a time. Claude
never pushes and never calls GitHub; the server does both.

## Labels

| Label | Meaning |
| --- | --- |
| `factory` | Ticket is managed by the factory |
| `stage:todo` | To do |
| `stage:planning` | Planner is writing the plan |
| `stage:awaiting-approval` | Plan is ready, waiting for you |
| `stage:building` | Builder is implementing |
| `stage:in-review` | PR is open and reviewed |
| `factory:blocked` | A step failed (the card shows Needs attention) |

Done = issue closed. Each ticket gets a worktree at `guess-the-country/factory-runs/issue-<N>` on branch
`factory/issue-<N>`.

## One-time setup

1. Create a fine-grained GitHub token for this repo only, with Issues, Pull requests and Contents all set to
   read/write.
2. Copy `dashboard/.env.example` to `dashboard/.env` and paste the token as `GITHUB_TOKEN`. `.env` is git-ignored.
3. Check the CLI login: `claude auth status` must show `loggedIn: true` (otherwise `claude auth login`).
4. Run the dashboard from a dedicated detached worktree of main, in your own terminal. Run the worktree command from
   the `mobile-app/` checkout: `git worktree add --detach ../factory-worktree origin/main`. Then copy
   `local.properties` into `../factory-worktree` (the per-ticket worktrees copy it from there; otherwise they rely on
   `ANDROID_HOME`), and `cd ../factory-worktree/factory/dashboard` (put the `.env` there).

## Run (from `factory/dashboard`)

```
npm run setup        # create the seven labels (safe to repeat)
npm start            # dashboard on http://localhost:4321
DEMO=1 npm start     # fake runner, no GitHub or Claude needed
npm run snapshot     # save a snapshot of the board
npm test
```

## If a step fails

The card shows Needs attention (red) and keeps its column. Read the comment on the issue for what went wrong, then
press Retry on the card.
