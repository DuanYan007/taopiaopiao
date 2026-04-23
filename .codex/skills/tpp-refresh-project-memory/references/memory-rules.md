# Memory Rules

Use this checklist when updating `/home/duanyan/.codex/memories/taopiaopiao-backend/`.

## Put In `stable-context.md`
- facts verified by current code or current repository guidance
- architecture truths that should remain the default starting point next session
- fixed repository boundaries, invariants, entry points, and review rules
- durable local conventions that future sessions will repeatedly need

## Put In `active-context.md`
- recent changes that are real now but may still settle further
- migration notes, pending follow-ups, and partially unified repository wording
- temporary local baselines or operational conclusions that may change later
- dated context that should help the next few sessions, not every future session

## Discard
- one-off command output, noisy debugging notes, and transient incidents without reusable lessons
- duplicate summaries already explained well in `README.md`, `AGENTS.md`, or existing skills
- stale history that no longer matches current code
- machine-only details, secrets, passwords, and personal shell knowledge

## Promote From Active To Stable Only If
- the fact now matches current code and current docs
- it is likely to remain true across multiple future sessions
- future work would start from the wrong assumption without it

## Remove Or Rewrite Immediately If
- memory contradicts current code
- memory still describes retired tasks, legacy tables, or removed request fields as active
- the note is only valuable as historical background and no longer helps future execution
- a newer note makes the old one redundant

## Writing Style
- keep bullets short
- prefer exact file paths over copied explanations
- replace stale bullets instead of appending conflicting ones
- memory is a fast-start layer for Codex, not a second documentation tree
