#!/bin/bash
# PreToolUse hook (Bash): block git commands that are hard to undo or touch main.
# Exit 2 blocks the call and tells Claude why. Everything else falls through to normal permissions.

raw=$(jq -r '.tool_input.command // empty')
[[ "$raw" == *git* ]] || exit 0

# Only look at what will actually run: drop heredoc bodies and quoted strings (commit messages, echo text),
# then split chained commands (&&, ||, ;, |, newlines) so each git invocation is checked on its own.
stripped=$(perl -0pe '
  s/<<-?\s*([\x27"]?)(\w+)\1[^\n]*\n.*?^\s*\2\s*$//gms;
  s/\x27[^\x27]*\x27//g;
  s/"(?:[^"\\]|\\.)*"//g;
' <<<"$raw")

cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null
branch=$(git branch --show-current 2>/dev/null)

block() { echo "Blocked by guard-git: $1" >&2; exit 2; }

while IFS= read -r seg; do
  # Must be a git invocation (optionally after env vars, sudo, or "git -C dir").
  [[ "$seg" =~ ^[[:space:]]*([A-Za-z_]+=[^[:space:]]*[[:space:]]+)*(sudo[[:space:]]+)?git([[:space:]]|$) ]] || continue
  sub=$(sed -E 's/^.*git([[:space:]]+-C[[:space:]]+[^[:space:]]+)?[[:space:]]+//' <<<"$seg")

  case "$sub" in
    push*)
      [[ "$sub" =~ (--force|--force-with-lease|[[:space:]]-[a-zA-Z]*f([[:space:]]|$)|[[:space:]]\+) ]] && block "force push is not allowed."
      [[ "$sub" =~ [[:space:]:](main|master)([[:space:]]|$) ]] && block "pushing to main is not allowed; push a feature branch."
      [[ "$branch" =~ ^(main|master)$ ]] && block "you are on $branch; create a branch before pushing."
      ;;
    commit*)
      [[ "$branch" =~ ^(main|master)$ ]] && block "don't commit on $branch; create a branch first (git switch -c <name>)."
      ;;
    reset*)
      [[ "$sub" =~ --hard ]] && block "git reset --hard discards work; ask the user to run it."
      ;;
    clean*)
      [[ "$sub" =~ [[:space:]]-[a-zA-Z]*f ]] && block "git clean -f deletes untracked files; ask the user to run it."
      ;;
  esac
done < <(sed -E 's/(&&|\|\||;|\|)/\n/g' <<<"$stripped")

exit 0
