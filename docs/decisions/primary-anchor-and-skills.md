# The primary's anchor holds only what must always be in mind, a skill of its own name holds each procedure, and its settings hold the hooks and the status line

**Kind:** Design

## Context

The old harness's always-loaded anchor grew to its 40 KB ceiling and buried the hard rules.
The anchor and its personal addendum must stay at most 10,000 bytes (I-35), while procedures are needed only when their trigger comes.
Claude Code makes a slash command only from a skill's own name, and passes the words after the name to the skill as its argument.
The captain adds the allow rules for `edi` in person, because a session editing its own permissions is self-modification.
The captain asked for a status bar that stays visible in the primary's session; Claude Code runs a `statusLine` command with the session's JSON on standard input and shows its output when it exits 0.

## Decision

`claude/primary/CLAUDE.md` holds the identity, the character ([personality](personality.md)), the voice, the hard rules I-01 to I-39 one line each, how the captain's word is recorded ([captains-word](captains-word.md)), how a turn ends in each presence, how memory is kept, and one trigger line per skill; the address word is not in it, the session-start digest names it ([primary-startup-context](primary-startup-context.md)).
Every command the anchor or a skill's trigger tells the captain to type is a user-invocable skill under `claude/skills/` whose name is the command: `sitrep`, `quiet`, `present`, `afk`, `demo`, `stow`, `personality` and `update`.
The procedures nobody types are skills with the `edi-` prefix: `edi-ship`, `edi-backlog-item`, `edi-scout`, `edi-board`, `edi-presence`, `edi-upkeep`, `edi-recover`, `edi-decision-log`, `edi-personality` and `edi-voice`; each names only commands that exist.
`ClaudeLayerTest` holds every command the anchor or a trigger names to a skill of that name, and every skill to a directory `ops/install.sh` links.
`claude/primary/settings.json` holds the seven hooks ([worker-sessions](worker-sessions.md)) and the status line, and no permission rule.
`edi statusline` posts the statusLine JSON to `/api/statusline` and prints the core's line, or why the core is not answering, always exiting 0; `StatusLineText` writes model and effort, context, session tokens, the two usage limits with their resets, the current directory's branch from one `git status --porcelain=v2 --branch`, and the cache hit ratio, refreshed every 60 seconds, with "Wait: none" after them while work is in flight and no `edi wait` is live ([wakes-and-turn-end](wakes-and-turn-end.md)).

## Why

A small anchor stays read and obeyed, procedures load only when their trigger comes, and nothing personal or self-granting is tracked.
A command that is a skill's name runs its procedure directly, and a test catches a command the anchor names that no skill answers.
The status line is presentation like every other report, so it lives with the presenters and their tests, and a status line grants nothing, so it may sit beside the hooks.

## Alternatives rejected

- Allow rules in the tracked settings - a session would grant itself what the captain is meant to grant.
- Custom commands under `.claude/commands/`, or one `presence` skill typed as `/presence quiet` - a second mechanism for the same job, or a longer command typed several times a day.
- The captain's own status line script, or the status line in user settings - it needs `jq` and cannot be tested with the presenters, and every session would run it.

## Consequences

- Good: AT-35 holds with room for a personal addendum, every command the anchor names works when typed, and the bar comes with the primary's settings.
- Bad: a procedure comes from a skill, so its trigger must be right, and a captain's own skill of the same name shadows EDI's.
- Bad: every status line refresh costs one call to the core and one git process.

## When to revisit

When the anchor nears its budget, Claude Code gives skills command aliases or changes how settings merge, or the statusLine fields change.

## How we got here

- 17/10/2026: slice S8 wrote the anchor and eleven skills, with settings holding only the hooks.
- 19/10/2026: the status line joined the primary's settings at the captain's request.
- 22/10/2026: six commands the anchor named answered "Unknown command", because no skill carried their names, so every typed command became a skill of its name (EDI-32).
- 23/10/2026: the anchor gained a Character section, and the `personality` and `edi-personality` skills joined it (EDI-31).
- 27/10/2026: EDI-95 marked a missing `edi wait` on the status line while work is in flight.
- 29/10/2026: the `edi-backlog-item` skill joined the `edi-` skills, the first with a script beside it, to put EDI's own work on the upstream backlog in one step (EDI-142).
- 03/11/2026: a request that takes more than one merge request goes through a scout's board first, and the `edi-ship` and `edi-scout` skills say so, since board-first features reached a sliced plan in a working day (EDI-75).
