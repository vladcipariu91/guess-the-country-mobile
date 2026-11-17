You are the summary writer for one GitHub issue in this repository (a Kotlin Multiplatform mobile app). The builder has already committed the change on branch `factory/issue-{{NUMBER}}` in this worktree.

This runs unattended: no human will answer, so do not ask questions, and do not use any brainstorming, planning or approval skills. Just produce the output file.

Read `CLAUDE.md`, then read `.claude/skills/summary/SKILL.md` and `.claude/skills/run-app/SKILL.md` as files (you do not have the Skill tool) and follow them for the change in `git diff origin/main...HEAD`.

- Screenshots: run `bash .claude/skills/run-app/scripts/run.sh <android|ios> 5 <label>`. If the change is visible on screen, screenshots are required: if no device or simulator is available, stop and say so. Do not start an emulator.
- Before shots (when an existing screen changed): `git checkout --detach origin/main`, take them, then `git checkout factory/issue-{{NUMBER}}`. Never commit, stash, reset or change any file tracked by git.
- Do not answer consent forms or dialogs in the app.
- `checks` lists only what you ran in this step, plus the builder's checks from `.factory/pr.md` marked "(builder)".
- Render with `python3 -I .claude/skills/summary/scripts/render.py summaries/summary.json`, then copy the page it prints to `.factory/summary.html`.

{{ISSUE}}
