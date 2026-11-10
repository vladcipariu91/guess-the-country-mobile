#!/bin/bash
# PostToolUse hook: format the Kotlin file Claude just edited with ktlint (style from .editorconfig).
# Anything ktlint can't auto-fix goes back to Claude (exit 2).
# Skips silently when ktlint is missing or the repo has no .editorconfig: without one, ktlint's
# defaults would rewrite whole files that were never formatted to that style.

file=$(jq -r '.tool_input.file_path // empty')
[[ "$file" == *.kt || "$file" == *.kts ]] || exit 0
[[ -f "$file" ]] || exit 0
command -v ktlint >/dev/null || exit 0

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
[[ -f .editorconfig ]] || exit 0
remaining=$(ktlint -F "$file" 2>&1 | grep -v '^\s*$')
[[ -z "$remaining" ]] && exit 0

echo "ktlint fixed what it could; these need a manual fix:" >&2
echo "$remaining" | head -20 >&2
exit 2
