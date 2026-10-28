---
name: stow
description: >-
  Owns curating the captain's memory files and keeping them within their startup budget.
  Load it when the captain types /stow, before a context reset, or when the memory check fails.
user-invocable: true
---

# stow

## The memory files

All live in `$EDI_HOME/memory/`:

- `profile.md`: the captain's machine-neutral rules, loaded at session start.
- `machine.md`: this machine's rules, loaded at session start.
- `learnings.md`: curated operational facts, loaded at session start: each is either a fact, dated with the day it was last confirmed, with its evidence and the rule it produced, or a workaround, which ends with "until" and the key of the ticket that removes it, such as "until EDI-69".
- `archive.md`: the cold tier, never loaded.
- `craft-rules.md`: the craftsmanship rules block, which rides in briefs and pipeline intents.

## Steps

1. Run `edi memory stow-list`: it lists undated entries, learnings due for a fresh check, expired and unticketed workarounds, repeated rule headings and any overrun of the budget.
2. Sweep this session for durable knowledge that lives only in the conversation, and for rules the captain set or changed.
3. Inspect the file before you change it; never append a duplicate.
4. Update in place: rewrite and prune, give every fact the date it was last confirmed, and delete a rule another one supersedes.
5. Confirm a stale learning with today's date, or move it to `archive.md`.
6. Delete an expired workaround: the ticket it waited on has landed.
7. Give an unticketed workaround a ticket and end it with "until" and that key, or turn it into a fact dated today.
8. Keep each fact in one place: what the registry, the machine profile or a decision record already holds does not belong in memory.
9. Run `edi memory check` until it passes: within the budget and without a repeated rule heading.

## Afterwards

- Memory loads at session start, so changes reach the next session.
- Tell the captain in one or two lines what changed in their memory and what moved to the archive.
