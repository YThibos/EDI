# ADR-158: Everything that waits on the captain is in the decision zone, whatever holds its task

**Date:** 21/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-046 put a decision whose task waits for another task under PARKED with its D number, and left it out of the decision zone and its count.
In a shadow run the report showed such a decision only under PARKED while the zone said "nothing open", which breaks I-19: every open decision appears in every report until it is answered.
Two other things waited on the captain without reaching the zone at all: a research board holding questions he had not answered, and a request ready for his review, whose address only the return brief's running line named.
The captain report, the console's sitrep page, the return brief and the primary's session-start digest are presenters over one fleet snapshot (ADR-012), so the classification belongs in the snapshot.

**Decision:** `FleetSnapshotQuery` gathers what waits on the captain into `WaitingOnTheCaptain`, the first part of `FleetSnapshot`, in one read transaction:
- `decisions`: every open decision in D-number order, whatever holds or blocks its task;
- `boards`: every listening board with open questions, as `WaitingBoard` with its task (ADR-159);
- `reviews`: every task in flight whose latest worker's done was accepted and whose request is still open, as `ReadyForReview` with its delivery.
`count()` is the number of all three, and the snapshot is quiet only when nothing waits on the captain and nothing is ongoing.
PARKED still lists every held task but one that waits only for its decision, with its D number and the tasks it waits for, so a blocked task's decision appears in the zone and its task under PARKED.
Every presenter draws its waiting part from the same rows:
- the captain report's NEEDS YOUR DECISION box groups the decision cards, a "Board for <task>: <n> questions waiting at <link>" line and a "Ready for your review: <task> - <address>" line by project, and counts all of them in its top rule;
- the sitrep page's need-you count and its "waiting on you" line count all three, its decision deck shows the cards, and a waiting list under the deck shows each board and each request with its full address; the all-clear panel shows only when nothing waits;
- the return brief's "Needs your decision" section lists the cards, the boards and the requests, and its running lines name only the work;
- the session-start digest's "Waiting on the captain" lists the same three for the primary.
A request leaves the zone when EDI sees it merged or closed.

**Motivation:** The captain finds everything that needs him in one place on every surface, and no surface says "nothing open" while something waits on him.

**Alternatives considered:**
- Keeping a blocked task's decision only under PARKED - the shadow run showed that the captain then misses it.
- Moving a blocked task's decision into the zone and out of PARKED - the captain would no longer see that answering it does not yet free the task.
- Counting a request as waiting from the moment it is recorded - a draft the worker is still validating is the worker's work, not the captain's review.
- Sorting in each presenter - the console, the brief and the report would drift apart again.

**Consequences:**
- Good: I-19 holds for decisions on held and blocked tasks, and boards and reviews reach the captain the same way decisions do.
- Bad: a blocked task's decision appears twice in the report, once as its card and once under PARKED.
- Bad: the golden cases `parked-calls` and `wrapping` no longer match the old renderer byte for byte, since they now show their blocked call in the zone (ADR-047).
- Bad: the snapshot read now also reads the listening boards and the delivery and latest worker of each task in flight.

**Revisit when:** Something else starts waiting on the captain, such as a go-ahead that is not a decision.

**Sources:** EDI rewrite specification, sections 5.4, 8.1 and 15.3 (I-19); ADR-012, ADR-039, ADR-046, ADR-047, ADR-107, ADR-159; `application.fleet.FleetSnapshotQuery`, `FleetSnapshot`, `WaitingOnTheCaptain`, `WaitingBoard`, `ReadyForReview`, `web.report.DecisionZone`, `CaptainReportPresenter`, `web.console.SitrepConsolePresenter`, `web.presence.ReturnBriefText`, `web.primary.SessionStartDigestText`, the console's `WaitingList`, `invariant.DecisionsAndReportingTest`.
