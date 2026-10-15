# ADR-026: Research before implementation is a piloted switch

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The captain: "Maybe we should even enforce an always-researcher policy, where the researcher is a higher model than the effective implementer. To be explored further."

**Decision:** `researchFirst: true` can be set per category; on `implementation-unplanned` and on `bugfix` with an unknown cause it makes a dispatch first create a `research` task whose deliverable is an enumerated plan with acceptance criteria, with the ship queued behind it and run as `implementation` with the plan in its brief.
The researcher's model is never weaker than the implementer's; EDI refuses a configuration that breaks this.
`mechanical`, `review-fix`, `documentation`, `operations` and the EDI project are exempt.
After ten piloted ships EDI compares them with comparable unplanned ones on total tokens, review rounds, findings at review and rework, and presents the comparison as a decision.

**Motivation:** Build the policy as a switch, measure it, and let the captain decide with numbers.

**Alternatives considered:**
- Make it the default now - unmeasured.
- Leave it out - the captain asked for it to be explored.

**Consequences:**
- Good: the decision to make it the default rests on evidence.
- Bad: piloted ships take an extra task.

**Revisit when:** The ten-ship comparison is ready.

**Sources:** EDI rewrite specification, section 8.6a (decision E22).
