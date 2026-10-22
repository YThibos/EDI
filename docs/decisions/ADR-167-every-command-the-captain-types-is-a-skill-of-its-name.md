# ADR-167: Every command the captain types is a skill of its name, and fifteen skills hold the primary's procedures

**Date:** 22/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** This record supersedes ADR-135, whose eleven skills named commands that did not exist: the anchor and the `edi-presence` skill told the captain to type `/afk`, `/quiet`, `/present` and `/demo`, and the anchor named `/stow` and `/update`.
Claude Code makes a slash command only from a skill's own name, so in a real primary session each of those six answered "Unknown command", while `/sitrep` worked because a skill carries that name.
A command's words after its name reach the skill as its argument, so `/quiet off` and `/demo off` are the `quiet` and `demo` skills with the argument `off`.
Away's mandate must be words the captain typed in the exchange, with or without `/afk` (ADR-115, ADR-122), so a skill passes them on verbatim.
Everything else ADR-135 decided still holds: section 10.1, I-35 and AT-35 keep the anchor at most 10,000 bytes with its personal addendum, the address word comes from the session-start digest (ADR-132), and the captain adds the allow rules for `edi` in person.

**Decision:** Every command the anchor or a skill's trigger tells the captain to type is a user-invocable skill under `claude/skills/` whose name is the command, the way `sitrep` is `/sitrep`.
Fifteen skills hold the procedures: `sitrep`, `quiet`, `present`, `afk`, `demo`, `stow` and `update` for the captain's commands, and `edi-ship`, `edi-scout`, `edi-board`, `edi-presence`, `edi-upkeep`, `edi-recover`, `edi-decision-log` and `edi-voice` for the procedures nobody types.
`quiet`, `present`, `afk` and `demo` each run their own `edi presence` call and say how their turn ends; `edi-presence` keeps what each presence means, the automatic changes and the return.
`edi-stow` and `edi-update` are renamed `stow` and `update`, and keep their procedures.
`claude/primary/CLAUDE.md` holds the identity, the voice, I-01 to I-39 one line each, how the captain's word is recorded (ADR-115), how a turn ends in present and in quiet or away, how memory is kept, and the skill triggers, and `claude/primary/settings.json` holds the seven hooks and the status line, and no permission rule, as ADR-135 decided.
`ClaudeLayerTest` holds every command the anchor or a skill's trigger names to a skill of that name, and every skill to the name of the directory `ops/install.sh` links.

**Motivation:** The captain's command is the skill's name, so typing it runs the procedure directly with no model routing in between, and a test catches a command named in the anchor that no skill answers.
A skill per command keeps each procedure to the few lines its command needs, while the meaning of the presences stays in one place.

**Alternatives considered:**
- Custom commands under `.claude/commands/` that load `edi-presence` - a second mechanism beside skills for the same job, which `ops/install.sh` would have to link too, and Claude Code folds commands into skills.
- One `presence` skill typed as `/presence quiet` - the captain's commands are `/afk` and `/demo` in ADR-030 and `/quiet` and `/present` in the anchor, and a longer command for something typed several times a day is a worse one.
- Keeping the `edi-` prefix on `stow` and `update` and adding thin command skills beside them - two skills for one procedure, with nothing gained from the prefix.

**Consequences:**
- Good: every command the anchor names works when the captain types it, and the build fails when one stops matching a skill.
- Good: a presence command loads only the lines it needs; `edi-presence` loads only when presence changes on its own or its meaning is asked.
- Bad: the command skills share the global namespace of Claude Code's commands, so a captain's own user-level skill of the same name shadows EDI's.

**Revisit when:** Claude Code gives skills command aliases, or the anchor nears its budget.

**Sources:** EDI rewrite specification, sections 2, 10.1, 10.3 and 16, I-35 (AT-35); ADR-017, ADR-030, ADR-031, ADR-115, ADR-121, ADR-122, ADR-132, ADR-134, ADR-135; `claude/primary/CLAUDE.md`, `claude/primary/settings.json`, `claude/skills/`, `ops/install.sh`, `claude.ClaudeLayerTest`, `invariant.MemoryAndCraftTest`, `application.primary.PrepareThePrimaryTest`.
