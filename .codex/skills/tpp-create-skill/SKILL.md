---
name: tpp-create-skill
description: Use when the user wants to create or update a repository-specific Codex skill for this project, including naming, scope, structure, and workflow.
---

# Scope
- Handles: creating or updating repository-specific skills for this project.
- Handles: turning a user-described workflow into a skill folder, `SKILL.md`, and optional references.
- Does not handle: executing the target business task itself unless the user separately asks for it.

# Trigger
- Use this skill when the user asks to create a new skill.
- Use this skill when the user wants to standardize or refine an existing skill.
- Use this skill when a repeated workflow should be extracted into a reusable skill.

# Read First
- `docs/skill-standard.md`
- `docs/skill-template.md`
- `docs/codex-workflow.md`
- `AGENTS.md`

# Workflow
1. Restate the target workflow the new skill should support.
2. Confirm the skill boundary: what it handles and what it must not handle.
3. Choose a name using `tpp-<action>-<domain>`.
4. Create the skill under `.codex/skills/<skill-name>/`.
5. Write `SKILL.md` using the repository template and fill `Scope`, `Trigger`, `Read First`, `Workflow`, `Guardrails`, `Verification`, and `Output`.
6. Add `references/` only when the skill needs longer, variant-specific material.
7. Keep the skill concise and operational; point back to `docs/` instead of duplicating repository context.
8. After creation, summarize when the new skill should be used and what session type it fits.

# Guardrails
- Do not create one skill for multiple unrelated workflows.
- Do not embed secrets, host passwords, or machine-only notes in a skill.
- Do not duplicate long repository documentation inside `SKILL.md`.
- Do not skip `Read First`; repository-specific skills must point to exact docs and code entry points.
- If the user only needs a one-off answer, do not create a skill unnecessarily.

# Verification
- Check the name matches `tpp-<action>-<domain>`.
- Check `SKILL.md` contains all required sections from `docs/skill-standard.md`.
- Check referenced docs and code paths are real.
- Check the skill is narrow enough to fit one stable workflow.

# Output
- State the created or updated skill name.
- State what task it is meant for.
- State the files added or changed.
- State any follow-up skill that should exist next.
