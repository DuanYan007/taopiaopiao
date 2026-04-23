---
name: tpp-prepare-push
description: Use when the task is to separate valid repository changes from local runtime artifacts, create a clean commit, and push it.
---

# Scope
- Handles: reviewing recent commits and current worktree to identify pushable changes.
- Handles: removing commit noise such as logs, pid files, temp files, and chmod-only drift from the final changeset.
- Handles: updating `.gitignore`, preparing a focused commit, and pushing when the user asks for it.
- Does not handle: implementing unrelated business requirements beyond what is already in the worktree.
- Does not handle: rewriting published remote history unless the user explicitly asks for it.

# Trigger
- Use this skill when push fails and the user suspects bad local files or oversized commits.
- Use this skill when the worktree is polluted by `.run/`, `logs/`, `.tmp/`, `nohup.out`, or similar runtime artifacts.
- Use this skill when the user asks to keep only effective changes, commit them, and push.

# Read First
- `AGENTS.md`
- `README.md`
- `/home/duanyan/.codex/memories/taopiaopiao-backend/stable-context.md`
- `.gitignore`
- `git status --short --branch`
- `git log --oneline -n 10`

# Workflow
1. Inspect branch status, recent commits, and current worktree before touching history.
2. Classify changed files into three groups: valid versioned changes, runtime artifacts, and permission-only noise.
3. If recent local commits contain invalid files, roll them back to local changes without deleting user work.
4. Update `.gitignore` for stable local artifacts such as `.run/`, `.tmp/`, `logs/`, `nohup.out`, and similar generated files.
5. Restore or discard chmod-only noise unless executable permission is intentionally required.
6. Stage only the valid files that should be versioned and verify the staged diff stat.
7. Create a concise Chinese commit message that reflects the actual purpose of the kept changes.
8. Push only after the staged content and commit history are confirmed clean; if push fails, inspect the real remote error before concluding why.

# Guardrails
- Do not commit `target/`, logs, pid files, temp Lua files, or `nohup.out`.
- Do not assume push failure is caused by file size without checking the actual commits and object sizes.
- Do not use destructive history rewrite on already-pushed commits unless the user explicitly approves it.
- Do not revert user source changes just because they are mixed with runtime artifacts; separate them first.
- Do not keep chmod-only churn unless the file truly needs execute permission.

# Verification
- `git status --short --branch`
- `git diff --cached --stat`
- `git log --oneline -n 3`
- confirm runtime artifacts are ignored by `.gitignore`
- capture and report the actual `git push` result

# Output
- State the skill name used.
- State which files were treated as effective changes and which were excluded as runtime noise.
- State the created commit id and message.
- State whether push succeeded, and if not, the exact failure category.
