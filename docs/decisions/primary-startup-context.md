# The primary starts from a system prompt composed at launch and a digest rebuilt from the store, with memory measured against a budget

**Kind:** Design

## Context

A restart is a non-event: all state lives in the store and git, and after a restart the primary rebuilds its picture from the store, not from memory (I-23).
Startup memory, the captain's profile, the machine's memory and the learnings, stays under a token budget, and superseded rules are deleted, not archived in place (I-33).
A hook's `additionalContext` reaches the model in full only up to 10,000 characters, while a system prompt also survives the conversation's compaction ([docs/verification/claude-code.md](../verification/claude-code.md)).
Claude Code can move a running session into its background service and relaunch it there; on 12 and 13 November 2026 the relaunched primary kept its settings file, hooks and permission mode but lost the `--append-system-prompt-file` option, and worked for hours without its anchor while its hooks ran as usual.
Claude Code loads an output style named by the session's `outputStyle` setting into the system prompt, from `.claude/output-styles/` in the session's working directory, and a value the `--settings` file states wins over the directory's own settings.

## Decision

`POST /api/primary/launch` runs `PrepareThePrimary`, which writes the composed prompt as the `edi-primary` output style, `.claude/output-styles/edi-primary.md` in the EDI home (`PrimaryOutputStyle`, `EdiHome.primaryOutputStyle`), and `state/primary/settings.json`, and answers the settings' path, the EDI home and a session name for `edi up` ([core-lifecycle](core-lifecycle.md)).
`claude/primary/settings.json` names that style in `outputStyle`, so the anchor rides on the settings every relaunch keeps, and `edi up` starts `claude --settings <file>` with no anchor option, so the anchor loads once.
The style keeps Claude Code's own coding instructions (`keep-coding-instructions: true`), as the appended prompt did.
`PrimaryPrompt` composes the prompt from the anchor bundled in the core, the checkout's `personal/anchor.local.md`, and each non-empty memory file loaded at session start, and refuses an anchor and addendum above 10,000 bytes.
`SessionStartDigestQuery` reads, from the store, the home and tmux, the session's standing, the address word, the personality ([personality](personality.md)), the previous primary's usage-limit stop, the fleet snapshot, every serving worker's health, the open outage list, the unacknowledged wakes, the presence, the memory check, the count of expired and unticketed workarounds, the files missing from the EDI home (`HomeSetup`), the sitrep page's opening ([console](console.md)) and whether the weekly upkeep is due, which it is on Mondays (`WeeklyUpkeep`).
`CheckTheSessionAnchor` holds the anchor present when the hook's `cwd` is the EDI home and the home's output style carries the anchor the core bundles; when it does not, the digest's first line after its title says the anchor is missing, why, and which file to read now.
`SessionStartDigestText` writes it as plain lines, the primary's `SessionStart` hook answers it as `additionalContext`, and it ends by asking for the one wake stream, `edi wait --follow` alone under the Monitor tool, while work is in flight, except in a standby session ([wakes-and-turn-end](wakes-and-turn-end.md)).
`MemoryFiles` reads `memory/profile.md`, `machine.md` and `learnings.md`; `StartupMemory` estimates tokens as characters divided by four against `memory.startupBudgetTokens` (18,000 when absent) and fails above the budget or on a heading repeated across the files.
`edi memory check` answers that check, `edi memory stow-list` lists repeated headings, undated entries and learnings unconfirmed for 90 days for `/stow`, and `DailyMemoryCheck` raises one notification when the memory first grows past its budget.
A learning is a fact, dated with the day it was last confirmed, or a workaround, which ends with "until" and the key of the ticket that removes it, such as "until EDI-69", and needs no date.
`WorkaroundTickets` resolves each key: one whose prefix belongs to a registered project on an external tracker through the `Tracker` port, where a ticket at or past `tracker.mergedStatus` has landed, and any other through the task store, where a task carrying the key has landed when `Task.hasLanded` holds, which is DONE until the lifecycle stages are stored.
`edi memory stow-list` lists a workaround whose ticket has landed as expired, to delete, one whose key no task or ticket carries as unticketed, to ticket or turn into a dated fact, and one the tracker cannot answer for as unchecked.
The digest's memory line counts expired and unticketed workarounds from the task store alone, so a workaround keyed on an external tracker counts as expired only when a task carrying its key has landed, and otherwise as neither.

## Why

Memory far larger than 10,000 characters reaches the primary whole, and survives compaction, through the system prompt.
The settings file is the one part of the launch that Claude Code carried into the background relaunch, and the output style it names is part of the system prompt, so the anchor stays with the session however it is relaunched, and a session that still lacks it hears so from the hook those same settings run.
Everything the digest says comes from the store, the home and tmux, and rules tied to the day rather than the session keep a restarted session's digest equal to the one before the crash.
A measured budget and a list of what to curate keep memory a map rather than a journal.

## Alternatives rejected

- The memory in the digest - cut to a preview above 10,000 characters.
- The anchor as `--append-system-prompt-file` alone - Claude Code dropped it when it relaunched the primary in its background service.
- The anchor as a `CLAUDE.md` in the EDI home - Claude Code loads a `CLAUDE.md` from every directory above a session's, so every worker in the home's `worktrees` would load the primary's anchor too.
- The anchor as the session-start hook's `additionalContext` - it shares the 10,000-character limit with the digest, and is conversation context rather than system prompt.
- Reading the Claude process's command line in the hook to see the anchor option - the relaunched session's process chain held no anchor option yet ran the same hook, and the option is gone.
- Telling the primary to read the memory files, or to run `edi sitrep` at its start - nothing makes it, and the hook does.
- The captain report as the digest - shaped for the captain, without worker health or the outage list.
- Counting tokens with a tokenizer - exact for one model and a dependency for an estimate that only needs to be stable.
- The digest asking the tracker about workarounds keyed on it - the session start would wait on JIRA, up to 40 seconds a call, and the digest would vary with its answers.
- Recording the resolved workarounds daily for the digest - a new table for counts up to a day old.

## Consequences

- Good: AT-23 compares the whole digest before a crash and after the restart, and AT-33 holds.
- Bad: memory curated during a session loads only in the next one, and a digest above 10,000 characters arrives as a preview.
- Bad: the anchor check reads the conditions under which Claude Code loads the style, not the system prompt itself, which no hook sees; a Claude Code that ignored `outputStyle` would go unnoticed.

## When to revisit

When Claude Code raises the limit on hook context or offers a reloadable system prompt, or the budget proves wrong in daily use.

## How we got here

- 16/10/2026: slice S4 built the session-start digest from the store.
- 17/10/2026: slice S8 composed the system prompt at launch, measured memory against its budget, and added presence, memory, setup and the weekly upkeep to the digest.
- 23/10/2026: the digest gained the personality, address and bad-news lines (EDI-31).
- 28/10/2026: a workaround names the ticket that removes it, `edi memory stow-list` flags it once that ticket lands or when no ticket exists, and the digest counts both from the store, as the captain chose (EDI-81).
- 02/11/2026: the digest asks for the wake stream rather than a background `edi wait`, and counts the wakes not yet handled (EDI-158).
- 13/11/2026: a primary relaunched in Claude Code's background service lost its anchor twice, so the composed prompt became the `edi-primary` output style its settings name, `edi up` dropped the anchor option, and the digest says first when a session lacks the anchor (EDI-268).
