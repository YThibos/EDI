# ADR-100: The research-first pilot compares planned ships with unplanned ones and asks the captain after ten

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 8.6a measures each piloted ship against comparable unplanned ones on total tokens (research plus ship, from the task's cost record), review rounds, findings at review and rework after the captain's review, and after ten piloted ships presents the comparison as a decision.
The cost record and the pipeline's state database already give each cleaned-up task's tokens (ADR-090); a decision always belongs to a held task (ADR-039).

**Decision:** Every ship dispatched with unplanned work in a category that can switch research first on, outside an exempt project, is recorded in `unplanned_ship`: with its research task when research came first, and without one when the switch was off, so the ships run while the switch is off are the comparison the pilot needs.
Cleanup also records the ship's review as `ReviewCounted`: the pipeline's review rounds on the branch, and the findings of each run's first review round, read only, unknown when the pipeline never reviewed the branch.
`ResearchFirstPilot` follows the event log (ADR-096) and, at each clean-up, measures the finished unplanned ships: tokens are the ship's total plus its research task's, the review figures are the recorded ones, and rework is the number of `review-fix` tasks keyed on the ship's ticket.
Once ten planned ships have finished, it adds the captain item `research-first-pilot` and raises on it, once, a decision with the averages of both groups: make research first the rule, switch it off, or pilot ten more ships; it recommends the rule when planned ships needed no more review rounds, findings or review fixes, switching it off when they needed more, and piloting further when no unplanned ship finished.

**Motivation:** The captain decides the default with the numbers he asked for, and the comparison is built only from records EDI keeps anyway.

**Alternatives considered:**
- Alternating research first between eligible ships - the switch would no longer mean what it says.
- Recommending by tokens - quality is never traded for cost (I-17), so review and rework decide and tokens are shown beside them.

**Consequences:**
- Good: the switch can be turned on whenever the captain wants, and the unplanned ships run before that are its baseline.
- Bad: the comparison is only as good as the baseline; with the switch on from the first day, it recommends piloting further.

**Revisit when:** The captain answers the pilot's decision.

**Sources:** EDI rewrite specification, sections 8.6a and 21.3 U-06 (decision E22, I-17); ADR-026, ADR-039, ADR-090, ADR-096, ADR-099; `application.dispatch.ResearchFirstPilot`, `ResearchBeforeImplementation`, `domain.dispatch.PilotComparison`, `ShipMeasure`, `UnplannedShip`, `domain.delivery.PipelineReview`, `TaskCost`, `adapter.pipeline.PipelineReviews`, `PipelineState`, `application.delivery.CleanUpTask`, `adapter.store.UnplannedShipStore`, `PipelineReviewStore`.
