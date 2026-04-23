---
name: tpp-review-example-domain
description: Use when the task is about <domain> and needs the fixed workflow for <goal>.
---

# Scope
- Handles:
- Does not handle:

# Trigger
- Use this skill when:

# Read First
- `README.md`
- `path/to/controller`
- `path/to/service`
- `path/to/consumer`

# Workflow
1. Read the required code and entry references before proposing changes.
2. Restate the current flow and assumptions.
3. List findings, risks, and change boundaries.
4. Wait for confirmation before implementation, if the task is not already implementation-ready.
5. Verify the affected path and state what was not verified.

# Guardrails
- Do not modify unrelated flows.
- Do not answer from memory when the code can be inspected.
- Do not skip idempotency, retry, or state-transition review for async chains.

# Verification
- `mvn -q -DskipTests compile`
- manual path check:
- log check:

# Output
- Findings first
- Then assumptions or open questions
- Then implementation summary and verification result
