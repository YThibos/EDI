# The Claude layer

The Claude layer is what EDI gives Claude Code: the primary's anchor and settings, the worker's brief, settings and system prompt, and the skills, all kept in [claude/](../claude/).
EDI's own settings carry hooks and the status line only; the captain's permission rules stay in the captain's own Claude Code settings.

## The primary session

The primary is the Claude Code session the captain talks to ([ADR-067](decisions/ADR-067-the-captains-session-is-primary.md)).
`edi up` starts it in the EDI home with [claude/primary/settings.json](../claude/primary/settings.json) and a system prompt the core composes at each start ([ADR-121](decisions/ADR-121-the-primarys-system-prompt-is-composed-at-launch.md)): the anchor [claude/primary/CLAUDE.md](../claude/primary/CLAUDE.md), the checkout's own `personal/anchor.local.md` when there is one, and the memory files `profile.md`, `machine.md` and `learnings.md` from `$EDI_HOME/memory/`.
The anchor holds EDI's character, the hard rules, the turn end and one line per skill ([ADR-167](decisions/ADR-167-every-command-the-captain-types-is-a-skill-of-its-name.md), [ADR-170](decisions/ADR-170-nine-personality-scales-replace-the-fixed-voice.md)).

## Hooks

The primary and the workers answer every Claude Code hook through `edi hook <event>` ([ADR-066](decisions/ADR-066-hooks-for-the-primary-and-workers.md), [ADR-167](decisions/ADR-167-every-command-the-captain-types-is-a-skill-of-its-name.md)).
The session the captain typed in last is the primary and any other stands by; a call made inside a Claude Code session names it, so a standby session's changes are refused.
The primary's turn ends with the captain report when it changed state, and with an `edi wait` running while work is in flight ([ADR-068](decisions/ADR-068-the-primarys-turn-end-rules.md)).
`edi wait` serves only the primary, and runs in the background until it exits printing what needs the primary, as soon as it happens ([ADR-069](decisions/ADR-069-wakes-are-durable-rows.md)).

```sh
edi wait                                            # the primary, in the background: exits printing what needs it
```

Each session starts from a digest the core rebuilds from the store ([ADR-076](decisions/ADR-076-the-session-start-digest.md), [ADR-132](decisions/ADR-132-the-session-start-digest-adds-presence-memory-and-setup.md)).

## The status line

[claude/primary/settings.json](../claude/primary/settings.json) also runs `edi statusline` as the session's status line, after every update and once a minute ([ADR-134](decisions/ADR-134-the-core-writes-the-primarys-status-line.md)).
It shows the model and effort, the context used, the session tokens with the cached and uncached split, the 5-hour and weekly limits with their resets, the branch of the working directory and the cache hit ratio.

## Workers

A worker starts with [claude/worker/settings.json](../claude/worker/settings.json), the system prompt [claude/worker/system-prompt.md](../claude/worker/system-prompt.md) and a brief written from [claude/worker/brief-template.md](../claude/worker/brief-template.md) (see [Workers](workers.md)).

## Skills

The anchor stays small; the procedures live in the skills under [claude/skills/](../claude/skills/).
`ops/install.sh` links them into `$EDI_HOME/.claude/skills/`, together with any in the checkout's `personal/skills/`.
Every command the captain types in the primary, such as `/sitrep` or `/quiet`, is a skill of that name, because Claude Code makes a slash command only from a skill's own name ([ADR-167](decisions/ADR-167-every-command-the-captain-types-is-a-skill-of-its-name.md)).

| Skill | Owns |
|---|---|
| [sitrep](../claude/skills/sitrep/SKILL.md) | the situation report, as boxed text or as the console's sitrep page |
| [edi-ship](../claude/skills/edi-ship/SKILL.md) | ships from intake to cleanup: the key, the branch, the dispatch category, the brief, the validation pipeline, delivery and landing |
| [edi-scout](../claude/skills/edi-scout/SKILL.md) | scouts: research and design tasks, their board, their completion and their promotion to a ship |
| [edi-board](../claude/skills/edi-board/SKILL.md) | arming a Lavish research board through EDI and routing its answers |
| [quiet](../claude/skills/quiet/SKILL.md) | `/quiet` and `/quiet off` |
| [present](../claude/skills/present/SKILL.md) | `/present` |
| [afk](../claude/skills/afk/SKILL.md) | `/afk` and the mandate its words give |
| [demo](../claude/skills/demo/SKILL.md) | `/demo` and `/demo off` |
| [edi-presence](../claude/skills/edi-presence/SKILL.md) | what present, quiet and away mean, the away mandate, the demo hold and the return brief |
| [edi-upkeep](../claude/skills/edi-upkeep/SKILL.md) | keeping EDI and the tools it drives current, Renovate's pull requests included |
| [edi-recover](../claude/skills/edi-recover/SKILL.md) | recovery after workers die or stop on a usage limit |
| [edi-decision-log](../claude/skills/edi-decision-log/SKILL.md) | decision records on projects that keep a decision log |
| [stow](../claude/skills/stow/SKILL.md) | curating the captain's memory files within their startup budget |
| [personality](../claude/skills/personality/SKILL.md) | `/personality`, which shows or changes the personality |
| [edi-personality](../claude/skills/edi-personality/SKILL.md) | the nine personality scales, the address word, the guard rails, the presets and the demo override |
| [edi-voice](../claude/skills/edi-voice/SKILL.md) | the register EDI keeps at every personality, and translating internal labels into the captain's words |
| [update](../claude/skills/update/SKILL.md) | updating EDI itself |
