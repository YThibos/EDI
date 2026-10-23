# One fleet snapshot query sorts the open work, and every surface is a presenter over it

**Kind:** Design

## Context

The old harness had two overview commands, a text report and a board, each assembling the fleet state its own way, and passing that state through arguments failed once the backlog passed 133 KB.
EDI shows the fleet in the captain report, the console's sitrep page, the return brief, the wake digest and the session-start digest.
Every open decision must appear in every report until it is answered (I-19), and fleet-sized data must stay small enough for any surface (I-36).

## Decision

The fleet state is one query, `FleetSnapshotQuery`, read from the store in one read transaction and never parsed from prose, reports or panes.
Each surface is a separate presenter over its `FleetSnapshot`.
The snapshot's first part, `WaitingOnTheCaptain`, holds everything that waits on the captain:

- `decisions`: every open decision in D-number order, whatever holds or blocks its task;
- `boards`: every listening research board with open questions ([research-boards](research-boards.md));
- `reviews`: every task in flight whose done was accepted and whose request is still open (`ReadyForReview`), until EDI sees it merged or closed.

Then come `ongoing` (in-flight tasks nothing holds, each an `OngoingTask` with its latest worker, pipeline run and delivery), `parked` (every held task except one that waits only for its decision, with its D number and the open tasks it waits for, `ParkedTask.whyParked`), and `queued`, each in the store's row order, plus the tasks landed in the last seven days and the answers of the last 24 hours.
The snapshot is quiet only when nothing waits on the captain and nothing is ongoing.
A worker status of `needs-decision` or `blocked` is addressed to the primary (`StatusReport.isForThePrimary`), so the sitrep page shows its task as `with EDI` rather than waiting on the captain, and the acknowledged steer that resolves it settles it (`Worker.settledBy`, [steering](steering.md)).
The sitrep page reads one document, `edi-sitrep.v1`, from `SitrepConsolePresenter`: the cards, the recent answers, at most ten landed and twenty charted tasks with their totals, the machine, the address word, the zone and the presence, so a 2 MB backlog gives a document of a few kilobytes.
The primary passes what its turn did as a small JSON document on standard input to `edi report`, which merges it with the snapshot ([captain-report](captain-report.md)).

## Why

One query keeps every surface consistent, separate presenters keep each format testable on its own, and large data travels through standard input and HTTP bodies, never arguments.
The captain finds everything that needs them in one place on every surface, and no surface says "nothing open" while something waits.

## Alternatives rejected

- A separate query or overview command per surface - duplicated state assembly, the old arrangement, and two views that can disagree.
- Sorting in each presenter - the console, the brief and the report would drift apart.
- Keeping a blocked task's decision only under PARKED - the captain then misses it.

## Consequences

- Good: a new surface is a new presenter, not a new query, and the classification is tested against a real store in milliseconds.
- Bad: the snapshot must carry everything any presenter needs, so its read touches the worker, pipeline, delivery and board stores, and a blocked task's decision appears both in the zone and under PARKED.

## When to revisit

When a presenter needs data the snapshot cannot carry cheaply, the work board needs the full queue, or something else starts waiting on the captain.

## How we got here

- 15/10/2026: the specification made the fleet state one query; slice S2 sorted the open work into decisions, ongoing, parked and queued for the captain report.
- 17/10/2026: slice S7 added ongoing work's worker and pipeline, landed work and recent answers for the console's sitrep page.
- 21/10/2026: a shadow run showed a blocked task's decision only under PARKED while the zone said nothing was open, and boards and ready requests not shown at all; everything that waits on the captain moved into one part of the snapshot, and a worker's question to the primary stopped looking like one to the captain.
