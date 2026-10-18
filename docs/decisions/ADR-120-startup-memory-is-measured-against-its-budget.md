# ADR-120: Startup memory is measured against its budget, curated from a stow list, and checked daily

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** I-33: startup memory (profile, machine memory and learnings) stays under the configured token budget, 18,000 today; superseded rules are deleted, not archived in place; AT-33: `edi memory check` fails above the budget and on a duplicated rule heading.
Section 8.8: the memory files live in the EDI home, each entry dated, and the check estimates tokens as characters divided by four.
U-13: the core checks the startup memory daily and raises one notification when it grows past the budget.

**Decision:** `MemoryFiles` reads `memory/profile.md`, `machine.md` and `learnings.md`, the files loaded at session start; `archive.md` is never loaded and `craft-rules.md` rides in briefs and pipeline intents.
`StartupMemory` estimates each file's tokens as its characters divided by four, rounded up, against `memory.startupBudgetTokens` from the machine profile (18,000 when absent), and fails when the total is above the budget or a Markdown heading repeats across the files, compared without case or spacing.
`GET /api/memory/check` answers the check as `MemoryCheck`, with status 409 when it fails, so `edi memory check` exits 1.
`StowList` lists what `/stow` curates: repeated rule headings, entries that carry no date, and learnings last confirmed more than 90 days ago; `edi memory stow-list` prints it with the tokens over the budget.
`DailyMemoryCheck` checks the memory a minute after the core starts and then every day, records each check in `memory_check`, and raises one Windows notification when a check finds the memory over its budget and the one before did not.

**Motivation:** A measured budget and a list of what to curate keep memory a curated map rather than a journal, and the core notices growth even when no one runs the check.

**Alternatives considered:**
- Counting tokens with a tokenizer - exact for one model and a dependency for an estimate that only needs to be stable.
- Notifying on every daily check above budget - a notification a day until the captain stows.

**Consequences:**
- Good: AT-33 holds, and the session-start digest says how the memory fits its budget.
- Bad: the estimate is rough for text that is not English prose.

**Revisit when:** The budget proves wrong in daily use, or Claude Code reports the tokens a session's system prompt costs.

**Sources:** EDI rewrite specification, sections 8.8 and 21.3 (U-13), I-33 (AT-33); `domain.memory.MemoryFile`, `MemoryEntry`, `StartupMemory`, `StowList`, `StowFinding`, `adapter.home.MemoryFiles`, `MachineProfile`, `application.memory.MemoryQuery`, `MemoryCheck`, `DailyMemoryCheck`, `adapter.store.MemoryCheckStore`, `web.memory.MemoryController`, `MemoryCheckText`, `StowListText`, `edi-cli` `MemoryCheckCommand`, `MemoryStowListCommand`.
