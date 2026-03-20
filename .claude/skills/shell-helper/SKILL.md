---
name: shell-helper
description: Generate and run safe shell commands for repository inspection and basic diagnostics. Use when the user asks to check files, list directories, inspect git status, or run non-destructive shell tasks.
disable-model-invocation: true
---

# Shell Helper

Use this skill for practical terminal tasks in the current project.

## Behavior

1. Understand the user's goal first.
2. Prefer read-only and non-destructive commands.
3. Explain the command briefly before running.
4. When useful, save reusable snippets under `${CLAUDE_SKILL_DIR}/reference.md`.

## Command safety rules

- Never run destructive commands (`rm -rf`, hard reset, force push) unless user explicitly asks.
- Prefer `rg` for search.
- For git inspection, use read-only commands first (`git status`, `git diff`, `git log`).

## Argument handling

User request:
$ARGUMENTS

If arguments are empty, ask for a specific objective.

## Optional helper script

When you need a compact workspace snapshot, run:

```bash
zsh "${CLAUDE_SKILL_DIR}/scripts/workspace_snapshot.sh"
```
