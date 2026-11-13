# The primary starts from a system prompt composed at launch and a digest rebuilt from the store, with memory measured against a budget

**Kind:** Design

## Context

A restart is a non-event: all state lives in the store and git, and after a restart the primary rebuilds its picture from the store, not from memory (I-23).
Startup memory, the captain's profile, the machine's memory and the learnings, stays under a token budget, and superseded rules are deleted, not archived in place (I-33).
A hook's `additionalContext` reaches the model in full only up to 10,000 characters, while a system prompt also survives the conversation's compaction ([docs/verification/claude-code.md](../verification/claude-code.md)).

## Decision

`POST /api/primary/launch` runs `PrepareThePrimary`, which writes `state/primary/system-prompt.md` and `state/primary/settings.json` and answers their paths and a session name for `edi up` ([core-lifecycle](core-lifecycle.md)).
`PrimaryPrompt` composes the prompt from the anchor bundled in the core, the checkout's `personal/anchor.local.md`, and each non-empty memory file loaded at session start, and refuses an anchor and addendum above 10,000 bytes.
`SessionStartDigestQuery` reads, from the store, the home and tmux, the session's standing, the address word, the personality ([personality](personality.md)), the previous primary's usage-limit stop, the fleet snapshot, every serving worker's health, the open outage list, the unacknowledged wakes, the presence, the memory check, the count of expired and unticketed workarounds, the files missing from the EDI home (`HomeSetup`), the sitrep page's opening ([console](console.md)) and whether the weekly gardening round is due, which it is on Mondays from the 09:00 weekly usage reset on (`WeeklyGardening`), so the digest offers the round with its upkeep check ([weekly-gardening](weekly-gardening.md)).
`SessionStartDigestText` writes it as plain lines, the primary's `SessionStart` hook answers it as `additionalContext`, and it ends by asking for the one wake stream, `edi wait --follow` alone under the Monitor tool, while work is in flight, except in a standby session ([wakes-and-turn-end](wakes-and-turn-end.md)).
`MemoryFiles` reads `memory/profile.md`, `machine.md` and `learnings.md`; `StartupMemory` estimates tokens as characters divided by four against `memory.startupBudgetTokens` (18,000 when absent) and fails above the budget or on a heading repeated across the files.
`edi memory check` answers that check, `edi memory stow-list` lists repeated headings, undated entries and learnings unconfirmed for 90 days for `/stow`, and `DailyMemoryCheck` raises one notification when the memory first grows past its budget.
A learning is a fact, dated with the day it was last confirmed, or a workaround, which ends with "until" and the key of the ticket that removes it, such as "until EDI-69", and needs no date.
`WorkaroundTickets` resolves each key: one whose prefix belongs to a registered project on an external tracker through the `Tracker` port, where a ticket at or past `tracker.mergedStatus` has landed, and any other through the task store, where a task carrying the key has landed when `Task.hasLanded` holds, which is DONE until the lifecycle stages are stored.
`edi memory stow-list` lists a workaround whose ticket has landed as expired, to delete, one whose key no task or ticket carries as unticketed, to ticket or turn into a dated fact, and one the tracker cannot answer for as unchecked.
The digest's memory line counts expired and unticketed workarounds from the task store alone, so a workaround keyed on an external tracker counts as expired only when a task carrying its key has landed, and otherwise as neither.

## Why

Memory far larger than 10,000 characters reaches the primary whole, and survives compaction, through the system prompt.
Everything the digest says comes from the store, the home and tmux, and rules tied to the day rather than the session keep a restarted session's digest equal to the one before the crash.
A measured budget and a list of what to curate keep memory a map rather than a journal.

## Alternatives rejected

- The memory in the digest - cut to a preview above 10,000 characters.
- Telling the primary to read the memory files, or to run `edi sitrep` at its start - nothing makes it, and the hook does.
- The captain report as the digest - shaped for the captain, without worker health or the outage list.
- Counting tokens with a tokenizer - exact for one model and a dependency for an estimate that only needs to be stable.
- The digest asking the tracker about workarounds keyed on it - the session start would wait on JIRA, up to 40 seconds a call, and the digest would vary with its answers.
- Recording the resolved workarounds daily for the digest - a new table for counts up to a day old.

## Consequences

- Good: AT-23 compares the whole digest before a crash and after the restart, and AT-33 holds.
- Bad: memory curated during a session loads only in the next one, and a digest above 10,000 characters arrives as a preview.

## When to revisit

When Claude Code raises the limit on hook context or offers a reloadable system prompt, or the budget proves wrong in daily use.

## How we got here

- 16/10/2026: slice S4 built the session-start digest from the store.
- 17/10/2026: slice S8 composed the system prompt at launch, measured memory against its budget, and added presence, memory, setup and the weekly upkeep to the digest.
- 23/10/2026: the digest gained the personality, address and bad-news lines (EDI-31).
- 28/10/2026: a workaround names the ticket that removes it, `edi memory stow-list` flags it once that ticket lands or when no ticket exists, and the digest counts both from the store, as the captain chose (EDI-81).
- 02/11/2026: the digest asks for the wake stream rather than a background `edi wait`, and counts the wakes not yet handled (EDI-158).
- 13/11/2026: the weekly upkeep became the gardening round, offered on Mondays once the weekly usage resets at 09:00 (EDI-165).
