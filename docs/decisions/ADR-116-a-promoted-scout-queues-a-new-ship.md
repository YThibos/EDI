# ADR-116: A scout is promoted by queueing a new ship on the captain's word

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** I-05: a scout report, diagnosis or recommendation never starts implementation; a ship needs the captain's go recorded on the task, and AT-05 refuses `edi task promote <scout>` without it.
A scout works on a `research/<KEY>-<n>` branch with a research brief, and a ship's branch kind and brief come from its dispatch category (ADR-051, ADR-055).

**Decision:** `PromoteScout` refuses anything but a scout that was not dropped, and needs the captain's word on the scout in the current exchange (ADR-115).
It queues a new ship through `AddTask`: its id is slugged from its title (the scout's, or the one given with `--title`), and it keeps the scout's projects, work key, base and delivery.
The ship's notes are the scout's notes, then the sentence that it was promoted on the captain's word with the captain's verbatim go, then the path of the scout's report when one exists.
`ScoutPromoted` on the scout names the ship; the scout itself is cleaned up as any scout.

**Motivation:** The ship starts from a fresh isolated copy on a branch its category names, with the captain's go in its intent, while the scout's findings travel as its notes.

**Alternatives considered:**
- Changing the scout into a ship in place - its research branch, its research brief and its running worker would carry over into delivery work.

**Consequences:**
- Good: a promotion is visible on both tasks, and the ship is dispatched like any other.
- Bad: promotion adds a task; the scout and the ship share a work key on different branch kinds.

**Revisit when:** The console offers promotion, or a scout's worker should continue as the ship's.

**Sources:** EDI rewrite specification, sections 10.3 and 15.5, I-05 (AT-05); ADR-051, ADR-055, ADR-115; `application.task.PromoteScout`, `PromotedScout`, `AddTask`, `domain.event.DomainEvent.ScoutPromoted`, `web.task.TaskController`, `edi-cli` `TaskPromoteCommand`.
