# ADR-046: Where the fleet snapshot puts open work

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-012 makes the fleet state one query with many presenters.
The old harness sorted its captain holds into buckets (live, aged, dated, blocked) read from its backlog lines, and its report drew the decision zone and the parked section from those buckets.
EDI's store keeps tasks in states, holds of three kinds that last until a date or the captain's word, the tasks each task waits for, and decisions that always hold their task (ADR-039).
Section 5.4 lists the snapshot's parts: open decisions, ongoing tasks, parked items and the queued backlog, plus recently landed work, worker health exceptions and pending refreshes, which need the workers, pipelines and deliveries of later slices.

**Decision:** `FleetSnapshotQuery` reads the in-flight tasks, the queued tasks and the open decisions in one read transaction and returns a `FleetSnapshot` of four lists:
- `decisions`: every open decision whose task waits for no unfinished task, in D-number order, each with its task (`OpenDecision`);
- `ongoing`: the in-flight tasks nothing holds;
- `parked`: every held task except one that waits only for its decision, in-flight tasks first and then queued ones, each with its open decision, if it has one, and the unfinished tasks it waits for (`ParkedTask`);
- `queued`: the queued tasks nothing holds.
Within each list the store's row order is kept.
A decision whose task waits for another task parks with its D number, as the old report parked a blocked call; a deferral or an approval parks, whether it lasts until a date or until the captain's word.
A blocker counts as finished only when it is done, as `Blocker.isResolved` already decides.
A decision never ages out of the decision list, as the old report kept its aged calls in the zone.
The other parts of section 5.4 join the snapshot with the slices that record them.

**Motivation:** Every open decision appears in every report by construction, either in the decision list or parked with its number (I-19), and every presenter, the captain report now and the console's sitrep page and the digests later, shows the same classification.

**Alternatives considered:**
- The old harness's buckets - its live and aged buckets exist because its holds could lack a card, and EDI's decisions cannot (ADR-039); a deferral carries its own date.
- Sorting in each presenter - the console and the digests would drift from the report.
- A read model of its own in SQL - a second shape of the same rows, the mapping layer section 5.3 rules out.

**Consequences:**
- Good: one class decides where open work waits, and it is tested against a real store in milliseconds.
- Bad: the snapshot carries whole tasks, notes included, so `edi report --json` grows with the notes of open work.

**Revisit when:** A presenter needs a part of section 5.4 that is not there yet, or the notes make the JSON too large.

**Sources:** EDI rewrite specification, sections 5.4, 8.1 and 15.1 (decision L1, I-19); ADR-012, ADR-039; `application.fleet.FleetSnapshotQuery`, `FleetSnapshot`, `OpenDecision`, `ParkedTask`, `FleetSnapshotQueryTest`.
