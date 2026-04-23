---
name: tpp-bootstrap-repo
description: Use when Codex enters this repository for the first time in a session and needs to load the essential project context before reviewing, designing, or implementing changes.
---

# Scope
- Handles: first-entry repository bootstrap for this project.
- Handles: loading the minimum necessary repository entry points, structure, runtime facts, and high-risk flow context into working context.
- Does not handle: detailed review or implementation of a specific business change.

# Trigger
- Use this skill at the start of a new session when the task is about this repository.
- Use this skill before reviewing payment, cancel, seckill, OpenResty, or load-test changes.
- Use this skill when the current session lacks project context or the task scope is still broad.

# Read First
- `AGENTS.md`
- `README.md`
- `/home/duanyan/.codex/memories/taopiaopiao-backend/stable-context.md`
- `/home/duanyan/.codex/memories/taopiaopiao-backend/active-context.md`
- root `pom.xml`
- `scripts/loadtest/README.md` when the task involves pressure testing

# Workflow
1. Read the repository entry points and memory first instead of scanning the whole codebase.
2. Summarize the project in a few concrete points: architecture, hot path, core services, infrastructure, and current focus.
3. Identify the task domain: seckill flow, payment chain, cancel chain, OpenResty gate, load testing, or interview docs.
4. Read only the code entry points needed for that domain, usually controller, service, consumer, listener, mapper, and relevant config.
5. Restate assumptions before proposing changes if the business semantics are not yet explicit.
6. Keep context lean; do not load unrelated modules into context.
7. If stable project knowledge is missing from repository entry points or memory, suggest or add the right update after the main task.

# Guardrails
- Do not start by reading random files across all modules.
- Do not answer from memory when the relevant docs or code can be inspected.
- Do not jump into implementation before restating the current flow for high-risk chains.
- Do not load machine-only secrets into versioned docs or skills.
- Do not treat the whole repository as equally important; focus first on the current hot path.

# Verification
- Confirm the summary includes: service layout, main hot path, consistency model, and local runtime dependencies.
- Confirm the chosen code entry points match the user task.
- Confirm no unrelated modules were read or modified without need.

# Output
- State the project summary briefly.
- State which domain this session should focus on.
- State which files were read to bootstrap context.
- State the next recommended skill or next inspection step.
