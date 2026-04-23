---
name: tpp-refresh-project-memory
description: Use when the task is to maintain the repository memory system by refreshing stable facts, recent context, and stale entries under /home/duanyan/.codex/memories/taopiaopiao-backend.
---

# Scope
- Handles: maintaining the repository memory system for this project.
- Handles: promoting stable facts into `stable-context.md`, capturing recent context in `active-context.md`, and removing stale or contradictory memory.
- Handles: merging current session outcomes with existing memory so later sessions start from current project reality instead of historical residue.
- Does not handle: implementing the business change itself unless the user separately asks for it.
- Does not handle: storing secrets, host-specific passwords, or noisy transient logs.
- Does not handle: turning memory into a second copy of repository docs.

# Trigger
- Use this skill when the user asks to refresh, clean up, or maintain project memory.
- Use this skill after a meaningful review or implementation session that changed default project understanding.
- Use this skill when current memory no longer matches code or repository docs.
- Use this skill when recent findings should be classified into stable facts, active context, or discard.

# Read First
- `AGENTS.md`
- `README.md`
- `/home/duanyan/.codex/memories/taopiaopiao-backend/stable-context.md`
- `/home/duanyan/.codex/memories/taopiaopiao-backend/active-context.md`
- `references/memory-rules.md`

# Workflow
1. Read the repository entry points and the existing memory files before changing anything.
2. Inspect only the code and docs touched by the current session or the domain the user asks to preserve.
3. Classify every candidate note into one of three buckets:
   - stable facts: architecture, invariants, fixed workflow entry points, durable conventions, repository boundaries
   - active context: recent decisions, migration notes, known follow-ups, temporarily useful local baselines, partially-settled repository changes
   - discard: transient errors, one-off experiments, stale historical residue, machine-only details, weakly verified observations
4. Apply the memory admission test before writing:
   - Is it verified by current code, `README.md`, `AGENTS.md`, or current repository guidance?
   - Will it still help a future session start correctly?
   - Is it stable enough for `stable-context.md`, or should it stay dated in `active-context.md`?
   - Would saving it reduce confusion, or just add noise?
5. Update `stable-context.md` only with facts that should remain true across sessions. Rewrite or remove stale entries instead of appending new bullets beside them.
6. Update `active-context.md` with short, dated notes for recent decisions, current follow-ups, or context that may still change.
7. Promote items from `active-context.md` to `stable-context.md` only when they have become default project reality.
8. Remove or rewrite active notes that are resolved, superseded, or no longer useful.
9. Keep memory concise. Prefer short bullets with exact file paths over copied explanations. Memory should help Codex start fast, not mirror the entire repo.

# Guardrails
- Do not copy large sections from repository docs into memory files.
- Do not store secrets, passwords, tokens, local-only shell history, or private machine notes.
- Do not put time-sensitive facts into `stable-context.md`; move them to `active-context.md` with an absolute date.
- Do not record assumptions that were not verified against current repository guidance or code.
- Do not let the memory files become a changelog of every session; store only reusable context.
- Do not treat memory as append-only. If an old fact is wrong now, replace or remove it.
- Do not save every useful observation; only keep facts that materially improve future repository understanding.
- Do not duplicate human-facing explanation that already belongs in `README.md` or `AGENTS.md`.

# Verification
- Confirm both memory files still exist and remain readable.
- Confirm each newly added fact is backed by current repository guidance or code inspected in the session.
- Confirm `stable-context.md` contains only durable facts and no session-specific chatter.
- Confirm `active-context.md` uses absolute dates for recent decisions or follow-ups.
- Confirm the update does not duplicate existing repository guidance unnecessarily.
- Confirm any stale or contradictory memory found in the session was removed or rewritten rather than left in place.
- Confirm the final memory set would help a new Codex session understand the current project quickly.

# Output
- State which memory files were updated.
- State which facts were promoted, which notes remained active, and which stale items were removed.
- State any important context that was intentionally not stored.
- State when this skill should be used again.
