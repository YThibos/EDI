# ADR-128: A voice lint keeps internal labels out of what the captain reads

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** I-22: captain-facing text uses plain English and the captain's nouns, and internal labels are translated (the `edi-voice` skill, ADR-031); AT-22: a lint over EDI-generated captain-facing strings (report sections, decision cards, notifications) rejects the internal-label list.
The primary writes the decision cards it raises and the wording of each turn in the captain report; EDI's own code writes the report's frame, the cards it raises itself, notifications, the return brief and the console's document.
A card EDI raises itself can carry a tracker's own text, such as a ticket summary.

**Decision:** `CaptainVoice` holds the internal labels with the plain words the captain hears instead, taken from the `edi-voice` table: isolated copy for worktree, cleanup for teardown, notification or monitoring for wake, watcher and heartbeat, worker for crewmate and second mate, and the plain wording for ask-user, needs-decision, fail-closed and fail-open and the like.
`CaptainVoice.internalLabelsIn` finds a label as a whole word, whatever its case, outside double quotes, code spans, links and paths, which may name a thing the captain needs to act on.
`edi decision raise` and `edi report --turn` refuse the primary's card or turn wording that carries a label, with one sentence naming the label and its plain words.
AT-22 lints every captain-facing string EDI's own code generates, across the report's golden cases, the cards EDI raises, every notification, every presence, the return brief and the console's sitrep document.

**Motivation:** The primary learns the rule from one refusal at the moment it writes, and EDI's own strings are held to the same list by a test.

**Alternatives considered:**
- Linting the cards EDI raises itself at runtime - a ticket summary from a tracker could carry a label and block the proposal.
- Leaving the rule to the skill alone - the voice audit of the old harness showed that prose rules drift.

**Consequences:**
- Good: an internal label can reach the captain only inside quotes, code or a path.
- Bad: a project whose own vocabulary uses a label names it in quotes.

**Revisit when:** The list grows a label that is also ordinary English.

**Sources:** EDI rewrite specification, sections 2 and 9, I-22 (AT-22); ADR-031; `domain.voice.CaptainVoice`, `InternalLabel`, `domain.decision.DecisionCard`, `web.decision.DecisionController`, `web.report.ReportController`, `Turn`, `claude/skills/edi-voice/SKILL.md`, `invariant.CaptainFacingStrings`.
