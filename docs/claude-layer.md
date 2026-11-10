# The Claude layer

The Claude layer is what EDI gives Claude Code: the primary's anchor and settings, the worker's brief, settings and system prompt, and the skills, all kept in [claude/](../claude/).
EDI's own settings carry hooks and the status line only; the captain's permission rules stay in the captain's own Claude Code settings.

## The primary session

The primary is the Claude Code session the captain talks to ([primary-role](decisions/primary-role.md)).
`edi up` starts it in the EDI home with [claude/primary/settings.json](../claude/primary/settings.json) and a system prompt the core composes at each start ([primary-startup-context](decisions/primary-startup-context.md)): the anchor [claude/primary/CLAUDE.md](../claude/primary/CLAUDE.md), the checkout's own `personal/anchor.local.md` when there is one, and the memory files `profile.md`, `machine.md` and `learnings.md` from `$EDI_HOME/memory/`.
It opens with no prompt submitted and waits for the captain to type, and a request for the board from any of its turns opens the sitrep page, even when the session's start already opened one ([opening-turn](decisions/opening-turn.md)).
The anchor holds EDI's character, the hard rules, the turn end and one line per skill ([primary-anchor-and-skills](decisions/primary-anchor-and-skills.md), [personality](decisions/personality.md)).

## Hooks

The primary and the workers answer every Claude Code hook through `edi hook <event>` ([worker-sessions](decisions/worker-sessions.md), [primary-anchor-and-skills](decisions/primary-anchor-and-skills.md)).
The session the captain typed in last is the primary and any other stands by; a call made inside a Claude Code session names it, so a standby session's changes are refused.
The primary's turn ends with the turn report when it changed state ([turn-report](decisions/turn-report.md)), and with its wake stream live while work is in flight ([wakes-and-turn-end](decisions/wakes-and-turn-end.md)).
The wake stream is `edi wait --follow`, which the primary starts alone with Claude Code's Monitor tool, once per session; it prints each wake the moment it is raised and keeps running ([wakes-and-turn-end](decisions/wakes-and-turn-end.md)).
`edi wait` serves only the primary: the core refuses a second stream while one is live, and `edi wait` refuses to start when its standard output leads nowhere.

```sh
edi wait --follow                                   # the primary, with the Monitor tool: prints each wake as it is raised
edi wait                                            # where Monitor is unavailable, in the background: exits printing the next wakes
```

Each session starts from a digest the core rebuilds from the store ([primary-startup-context](decisions/primary-startup-context.md)).

## The status line

[claude/primary/settings.json](../claude/primary/settings.json) also runs `edi statusline` as the session's status line, after every update and once a minute ([primary-anchor-and-skills](decisions/primary-anchor-and-skills.md)).
It shows the model and effort, the context used, the session tokens with the cached and uncached split, the 5-hour and weekly limits with their resets, the branch of the working directory and the cache hit ratio.
While work is in flight and no wake stream is live, it ends with "Wait: none" in dark red; `edi health` says the same in words.

## Workers

A worker starts with [claude/worker/settings.json](../claude/worker/settings.json), the system prompt [claude/worker/system-prompt.md](../claude/worker/system-prompt.md) and a brief written from [claude/worker/brief-template.md](../claude/worker/brief-template.md) (see [Workers](workers.md)).

## Skills

The anchor stays small; the procedures live in the skills under [claude/skills/](../claude/skills/).
`ops/install.sh` links them into `$EDI_HOME/.claude/skills/`, together with any in the checkout's `personal/skills/`.
Every command the captain types in the primary, such as `/sitrep` or `/quiet`, is a skill of that name, because Claude Code makes a slash command only from a skill's own name ([primary-anchor-and-skills](decisions/primary-anchor-and-skills.md)).

| Skill | Owns |
|---|---|
| [sitrep](../claude/skills/sitrep/SKILL.md) | the situation report, as boxed text or as the console's sitrep page |
| [edi-ship](../claude/skills/edi-ship/SKILL.md) | ships from intake to cleanup: the key, the branch, the dispatch category, the brief, the validation pipeline, delivery and landing |
| [edi-backlog-item](../claude/skills/edi-backlog-item/SKILL.md) | putting work on the EDI project on its upstream backlog: its GitHub issue with the full spec, milestone, parent, blockers and labels, keyed by the issue's number, and the hold on the local task, in one scripted step |
| [edi-scout](../claude/skills/edi-scout/SKILL.md) | scouts: research and design tasks, their board, their completion and their promotion to a ship |
| [edi-board](../claude/skills/edi-board/SKILL.md) | arming a Lavish research board through EDI and routing its answers |
| [quiet](../claude/skills/quiet/SKILL.md) | `/quiet` and `/quiet off` |
| [present](../claude/skills/present/SKILL.md) | `/present` |
| [afk](../claude/skills/afk/SKILL.md) | `/afk` and the mandate its words give |
| [demo](../claude/skills/demo/SKILL.md) | `/demo` and `/demo off` |
| [edi-presence](../claude/skills/edi-presence/SKILL.md) | what present, quiet and away mean, the away mandate, the demo hold and the return brief |
| [edi-upkeep](../claude/skills/edi-upkeep/SKILL.md) | keeping EDI and the tools it drives current, Renovate's pull requests included, and sorting the open sparks in the weekly round |
| [edi-recover](../claude/skills/edi-recover/SKILL.md) | recovery after workers die or stop on a usage limit |
| [introspect](../claude/skills/introspect/SKILL.md) | introspection: from the store's evidence of a failure of the primary's own to hard rules on the upstream backlog of the EDI project, with a script gathering the evidence |
| [edi-decision-log](../claude/skills/edi-decision-log/SKILL.md) | decision records on projects that keep a decision log |
| [stow](../claude/skills/stow/SKILL.md) | curating the captain's memory files within their startup budget |
| [personality](../claude/skills/personality/SKILL.md) | `/personality`, which shows or changes the personality |
| [edi-personality](../claude/skills/edi-personality/SKILL.md) | the nine personality scales, the address word, the guard rails, the presets and the demo override |
| [edi-voice](../claude/skills/edi-voice/SKILL.md) | translating internal labels into the captain's words; the register is in the anchor's Voice section |
| [update](../claude/skills/update/SKILL.md) | updating EDI itself |
