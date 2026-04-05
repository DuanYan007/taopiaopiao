# Codex Workflow

## Default Collaboration Pattern
Use Codex in four steps for this repository:
1. Review: ask it to read the relevant docs and code first.
2. Design: ask for a narrow change plan and tradeoffs.
3. Implement: confirm scope, then let it edit only the agreed modules.
4. Verify: require compile, runtime checks, and explicit statement of what was not verified.

## Skill Rule
When a task becomes repetitive or has a fixed review path, convert it into a dedicated skill. Follow `docs/skill-standard.md` and start from `docs/skill-template.md`.

## Good Prompt Pattern
Include these five points in each task:
- target flow, for example payment or cancel chain
- scope limits, for example “do not touch other problems”
- consistency target, for example eventual consistency
- expected output, for example review first or implement directly
- verification bar, for example compile plus manual test script

Example:
```text
先审阅支付成功后的生产消费全链路，只处理支付链路，不修改取消链路。目标是最终一致性。先列问题，再给修改方案。
```

## When Codex Works Best Here
- reviewing MQ producer-consumer chains
- tracing Redis and Lua behavior
- adding pressure-test scripts and observation steps
- explaining design tradeoffs for interviews

## Guardrails
- Do not let Codex answer from memory when the code can be read directly.
- For payment, cancel, Redis, or OpenResty changes, require end-to-end review before editing.
- Ask it to separate findings, assumptions, implementation, and verification.
- Ask it to record stable project knowledge back into `docs/` after major changes.
