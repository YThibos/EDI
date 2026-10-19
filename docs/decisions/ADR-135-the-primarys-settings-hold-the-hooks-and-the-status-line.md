# ADR-135: The primary's anchor holds only what must always be in mind, eleven skills hold the procedures, and its settings hold the hooks and the status line

**Date:** 19/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** This record supersedes ADR-131, whose settings for the primary held the seven hooks and nothing else; the status line (ADR-134) is the first setting beside them.
Section 10.1: the anchor holds only identity and voice, the hard rules I-01 to I-39 in one line each by id, the turn-end rule and one-line skill triggers; everything procedural lives in a skill or behind `edi --help`; I-35 and AT-35: the anchor and its personal addendum stay at most 10,000 bytes.
Section 10.3 lists the skills and their triggers; the captain's own skills stay user-level.
The harness is person-agnostic (ADR-017), while the primary addresses the captain by the configured address word (ADR-031).
The captain adds the allow rules for `edi` in person, because editing Claude Code's settings is self-modification the classifier refuses.

**Decision:** `claude/primary/CLAUDE.md` holds the identity, the voice, I-01 to I-39 one line each, how the captain's word is recorded (ADR-115), how a turn ends in present and in quiet or away, how memory is kept, and the skill triggers, well within the 10,000-byte budget of AT-35.
The address word is not written in the anchor: the session-start digest names it (ADR-132).
Eleven skills under `claude/skills/` hold the procedures: `sitrep`, `edi-ship`, `edi-scout`, `edi-board`, `edi-presence`, `edi-upkeep`, `edi-recover`, `edi-decision-log`, `edi-stow`, `edi-voice` and `edi-update`, each naming only commands that exist.
`claude/primary/settings.json` holds the seven hooks and the status line, and no permission rule.

**Motivation:** A small anchor stays read and obeyed, the procedures load only when their trigger comes, and nothing personal or self-granting is tracked.
A status line grants nothing, so it sits beside the hooks without breaking that rule.

**Alternatives considered:**
- The old harness's long anchor - it grew to its ceiling and buried the hard rules.
- Allow rules in the tracked settings - a session would grant itself the permissions the captain is meant to grant.
- The status line in the captain's user settings - every Claude Code session would run it, not only the primary, and EDI could not keep it in step with `edi statusline`.

**Consequences:**
- Good: AT-35 holds with room for a personal addendum, and the primary's bar comes with its settings.
- Bad: a procedure the primary needs comes from a skill, so its trigger must be right.

**Revisit when:** The anchor nears its budget, or Claude Code changes how skills are found or how settings are merged.

**Sources:** EDI rewrite specification, sections 2, 10.1, 10.3 and 16, I-35 (AT-35); ADR-017, ADR-031, ADR-115, ADR-121, ADR-131, ADR-132, ADR-134; `claude/primary/CLAUDE.md`, `claude/primary/settings.json`, `claude/skills/`, `invariant.MemoryAndCraftTest`, `application.primary.PrepareThePrimaryTest`.
