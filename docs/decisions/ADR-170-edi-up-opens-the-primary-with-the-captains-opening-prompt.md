# ADR-170: edi up opens the primary with the captain's opening prompt

**Date:** 23/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** `edi up` opens the primary in a new tmux session running `claude --settings ... --append-system-prompt-file ...` (ADR-121, ADR-129), and the session then waits for the captain to type, so EDI never speaks first.
The captain asked for the primary to open with the prompt "Hello EDI, SITREP board, please." already submitted, so that the first turn is EDI's greeting and the board sitrep.
The captain also asked for no output style and kept the global Claude Code memory loading, so nothing is added to the EDI home.
On Claude Code 2.1.308 a prompt after `--` in an interactive session is submitted at once: `SessionStart` fires with `source: "startup"` first, then `UserPromptSubmit` with the prompt, and that hook's `additionalContext` reaches the model ([docs/verification/claude-code.md](../verification/claude-code.md)).

**Decision:** `PrimaryLaunch.claude()` ends the primary's command line with `--` and the opening prompt "Hello EDI, SITREP board, please.", exactly as the captain wrote it.
The prompt travels only in the command that `tmux new-session` starts; switching to a running primary's session carries no prompt, and a session name that already exists is refused by tmux before any `claude` starts.
The prompt reaches the core like any prompt the captain types: it claims the primary role, and when the captain was away the return brief comes with it (ADR-127).

**Motivation:** The captain's own words make the first turn without any new mechanism, and a positional prompt is the one way Claude Code takes a first turn at launch.
Passing it after `--` matches the worker launch, so a prompt can never be read as a flag (ADR-054).

**Alternatives considered:**
- Typing the prompt into the pane once the pane probe finds it idle (ADR-057) - it would wait for the start-up screens to settle, and only the core's adapters drive a pane, while a positional prompt needs nothing beyond the command line.
- Opening the sitrep from the session-start digest alone - the digest is context, not a turn, so EDI would still not speak first.
- An output style or a `CLAUDE.md` in the EDI home - the captain declined both.

**Consequences:**
- Good: every new primary opens with EDI greeting the captain and showing the board sitrep, without a key pressed.
- Good: a resumed, cleared or compacted primary is not greeted again, because none of them starts through `edi up`.
- Bad: on a machine whose profile opens the sitrep page at session start (ADR-151), the opening turn's `edi sitrep --board` opens the page a second time.

**Revisit when:** The captain changes the opening words, or Claude Code stops submitting a positional prompt in an interactive session.

**Sources:** The captain's request of 23/10/2026 (EDI-43); ADR-054, ADR-121, ADR-127, ADR-129, ADR-151; [docs/verification/claude-code.md](../verification/claude-code.md); `cli.PrimaryLaunch`, `cli.PrimaryWindow`, `claude/skills/sitrep/SKILL.md`.
