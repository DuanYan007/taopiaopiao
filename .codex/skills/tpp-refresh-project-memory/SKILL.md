---
name: tpp-refresh-project-memory
description: Use when the task is to distill stable repository context or recent architectural decisions into long-term memory under /home/duanyan/.codex/memories/taopiaopiao-backend.
---

# Scope
- Handles: extracting durable project facts, current architecture decisions, and follow-up risks from the current repository session.
- Handles: updating the fixed long-term memory files for this repository under `/home/duanyan/.codex/memories/taopiaopiao-backend/`.
- Does not handle: implementing the business change itself unless the user separately asks for it.
- Does not handle: storing secrets, host-specific passwords, or noisy transient logs.

# Trigger
- Use this skill when the user asks to update long-term memory for this repository.
- Use this skill after a meaningful review or implementation session that produced durable project knowledge.
- Use this skill when stable context is likely to be needed in later sessions but is not yet captured in docs or memory files.

# Read First
- `AGENTS.md`
- `docs/system-map.md`
- `docs/business-flow/seckill.md`
- `docs/invariants.md`
- `docs/codex-workflow.md`
- `/home/duanyan/.codex/memories/taopiaopiao-backend/stable-context.md`
- `/home/duanyan/.codex/memories/taopiaopiao-backend/active-context.md`

# Workflow
1. Read the repository docs and the existing memory files before adding anything new.
2. Inspect only the code and docs touched by the current session or the domain the user asks to preserve.
3. Classify candidate notes into three buckets:
   - stable facts: architecture, invariants, fixed workflow entry points, durable config conventions
   - active context: recent decisions, known follow-ups, unresolved risks, migration notes
   - discard: transient errors, machine-only details, one-off experiments, unstable observations without evidence
4. Update `stable-context.md` only with durable facts that should remain true across sessions.
5. Update `active-context.md` with dated notes for current design decisions, follow-ups, or known edge cases that may change later.
6. Merge duplicates and rewrite contradictions instead of appending near-identical notes.
7. Keep memory concise. Prefer short bullets with exact file paths over long copied explanations.

# Guardrails
- Do not copy large sections from repository docs into memory files.
- Do not store secrets, passwords, tokens, local-only shell history, or private machine notes.
- Do not put time-sensitive facts into `stable-context.md`; move them to `active-context.md` with an absolute date.
- Do not record assumptions that were not verified against repository docs or code.
- Do not let the memory files become a changelog of every session; store only reusable context.

# Verification
- Confirm both memory files still exist and remain readable.
- Confirm each newly added fact is backed by repository docs or code inspected in the session.
- Confirm `stable-context.md` contains only durable facts and no session-specific chatter.
- Confirm `active-context.md` uses absolute dates for recent decisions or follow-ups.
- Confirm the update does not duplicate existing repository docs unnecessarily.

# Output
- State which memory files were updated.
- State the durable facts or active notes that were captured.
- State any important context that was intentionally not stored.
- State when this skill should be used again.
