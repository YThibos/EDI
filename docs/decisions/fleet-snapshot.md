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

- `decisions`: every open decision in D-number order that no board in `boards` binds, whatever holds or blocks its task;
- `boards`: every listening research board with open questions or an open decision it binds, each with the open decisions it binds (`WaitingBoard.decisions`), because the captain answers those on the board ([research-boards](research-boards.md));
- `reviews`: every task in flight whose done was accepted and whose request is still open (`ReadyForReview`), until EDI sees it merged or closed.

Then come `ongoing` (in-flight tasks nothing holds, each an `OngoingTask` with its latest worker, pipeline run and delivery), `parked` (every held task except one that waits only for its decision, with its D number and the open tasks it waits for, `ParkedTask.whyParked`), and `queued`, each in the store's row order, plus the tasks landed in the last seven days and the answers of the last 24 hours.
`WaitingOnTheCaptain.count` counts each decision, board and review once.
The snapshot is quiet only when nothing waits on the captain and nothing is ongoing.
A worker status of `needs-decision` or `blocked` is addressed to the primary (`StatusReport.isForThePrimary`), so the sitrep page shows its task as `with EDI` rather than waiting on the captain, and the acknowledged steer that resolves it settles it (`Worker.settledBy`, [steering](steering.md)).
The sitrep page reads one document, `edi-sitrep.v1`, from `SitrepConsolePresenter`: the cards, the recent answers, at most ten landed and twenty charted tasks with their totals, the machine, the address word, the zone and the presence, so a 2 MB backlog gives a document of a few kilobytes.
Each ongoing task (`ConsoleSitrep.Ongoing`) carries its work key from its intake as its own `key` field, null for an unkeyed task, and a title with a leading copy of that key taken off, together with the separator after it (spaces, a colon, a bar or a dash); the key counts as leading only when it ends where the title's word ends, so `EDI-31` never takes the start off a title beginning with `EDI-310`, and the match ignores case.
The console's `OngoingList` shows the key first in the mono face and the ion colour, then the title, and an unkeyed task shows its title alone, with no placeholder.
The primary passes what its turn did as a small JSON document on standard input to `edi report`, which merges it with the snapshot ([captain-report](captain-report.md)).

## Why

One query keeps every surface consistent, separate presenters keep each format testable on its own, and large data travels through standard input and HTTP bodies, never arguments.
A work key in its own field lets the page style it apart from the title, and taking a leading copy off the title in the core means the key is never shown twice, whatever the console does.
The captain finds everything that needs them in one place on every surface, and no surface says "nothing open" while something waits.

## Alternatives rejected

- A separate query or overview command per surface - duplicated state assembly, the old arrangement, and two views that can disagree.
- Sorting in each presenter - the console, the brief and the report would drift apart.
- Keeping a blocked task's decision only under PARKED - the captain then misses it.
- Leaving a board's decision among the cards - the captain reads its full card in the report beside the board he answers it on.
- Each presenter working out which decisions a board binds - the report, the console and the briefs would drift apart.
- Gluing the work key into the ongoing title in the core - the page could no longer style the key, and every other surface reading the title would inherit the prefix.
- Leaving the work key out when the title already starts with it - the same task would show its key styled or unstyled depending on how it was titled.
- A work key on every block of the sitrep page - the captain asked for the Ongoing block only.

## Consequences

- Good: a new surface is a new presenter, not a new query, and the classification is tested against a real store in milliseconds.
- Bad: the snapshot must carry everything any presenter needs, so its read touches the worker, pipeline, delivery and board stores, and a blocked task's decision appears both in the zone and under PARKED.
- Bad: an ongoing title on the sitrep page can differ from the stored title by its leading key.

## When to revisit

When a presenter needs data the snapshot cannot carry cheaply, the work board needs the full queue, something else starts waiting on the captain, or another block of the sitrep page should show work keys.

## How we got here

- 15/10/2026: the specification made the fleet state one query; slice S2 sorted the open work into decisions, ongoing, parked and queued for the captain report.
- 17/10/2026: slice S7 added ongoing work's worker and pipeline, landed work and recent answers for the console's sitrep page.
- 21/10/2026: a shadow run showed a blocked task's decision only under PARKED while the zone said nothing was open, and boards and ready requests not shown at all; everything that waits on the captain moved into one part of the snapshot, and a worker's question to the primary stopped looking like one to the captain.
- 23/10/2026: the captain could not tell which key a running task books on, so each ongoing task on the sitrep page leads with its work key (EDI-44).
- 26/10/2026: the report printed a scout's board decisions with their full cards beside the board the captain answers them on, so a waiting board carries the open decisions it binds and every surface shows each under its board (EDI-51).
