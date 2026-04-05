# Skill Standard

## Goal
Use one stable format for repository-specific Codex skills so different tasks can be handled in separate sessions without losing consistency.

## Naming
- Use kebab-case.
- Recommended pattern: `tpp-<action>-<domain>`.
- Examples: `tpp-bootstrap-repo`, `tpp-review-payment-chain`, `tpp-analysis-loadtest`.

## Directory Layout
```text
skill-name/
├── SKILL.md
├── references/
│   └── optional-topic.md
├── scripts/
│   └── optional-helper.sh
└── agents/
    └── openai.yaml
```

## Required SKILL.md Sections
1. `Scope`: what this skill handles and what it must not handle.
2. `Trigger`: when to use this skill.
3. `Read First`: exact docs and code paths to inspect before acting.
4. `Workflow`: the fixed working sequence.
5. `Guardrails`: common mistakes and forbidden shortcuts.
6. `Verification`: minimum checks before closing the task.
7. `Output`: what the final answer should contain.

## Writing Rules
- Keep `SKILL.md` concise and operational.
- Do not repeat repository basics already covered in `docs/`.
- Use references for long examples or variant-specific details.
- One skill should serve one workflow, not an entire subsystem.
- Do not place secrets, host passwords, or machine-only data in skills.

## Session Rule
- One session should primarily use one main skill.
- If the task changes from payment to load testing or OpenResty, open a new session and switch skills.
- Only combine skills when the workflows truly overlap.

## Current Recommended Skill Set
- `tpp-create-skill`: create or update repository-specific skills from user-described workflows.
- `tpp-bootstrap-repo`: initial repository reading and task scoping.
- `tpp-review-seckill-flow`: lock-seat hot path review.
- `tpp-review-payment-chain`: paid-event producer and consumer review.
- `tpp-review-cancel-chain`: timeout and cancel flow review.
- `tpp-change-openresty-gate`: OpenResty and Lua gate work.
- `tpp-analysis-loadtest`: k6 test execution and result analysis.
- `tpp-write-interview-docs`: interview storytelling and architecture write-up.
