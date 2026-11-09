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
- `reviews`: every task in flight whose done was accepted and whose request is still open and out of draft (`ReadyForReview`), while no pipeline run of the task still changes the request and no message waits unacknowledged in its inbox, until EDI sees it merged or closed;
- `changing`: every such request that is still a draft, whose pipeline run still changes it or whose inbox still holds an unacknowledged steer, and every open request of a task in flight whose event log holds an accepted done its latest worker no longer stands by (`ChangingRequest`), with why it is changing (`ChangingRequest.whyChanging`: "worker back at work", "still a draft", the pipeline's step and the steers' numbers); a first-pass draft whose task never had a done accepted is in neither list.

Then come `ongoing` (in-flight tasks nothing holds, each an `OngoingTask` with its latest worker, pipeline run and delivery), `parked` (every held task except one that waits only for its decision, with its D number and the open tasks it waits for, `ParkedTask.whyParked`), and `queued`, each in the store's row order, plus the tasks landed in the last seven days and the answers of the last 24 hours.
EDI keeps observing a handed-over task's pipeline while its stored run is running or still changes the request (`ObservePipelines`), so the run is seen to finish, and a run that ended without passed checks is seen to pass them or be followed by a green run, and the request becomes ready for review.
A request is ready only on green CI at its last pushed head, which the pipeline records as passed checks (`PipelineRun.checksPassed`), so its latest run still changes it (`PipelineRun.stillChangesTheRequest`) unless that run completed, or its checks passed and either it has ended or its only running step is the CI watch.
The pipeline's last step keeps watching CI after the checks pass, until the request merges, and re-arms each time the base advances, so a request whose checks passed reads as ready while that watch runs, and once the watch is handed off.
It reads as changing again once the pipeline clears the passed checks: when CI on the head fails or is pending again, when the watch starts a fix, and when the fix pushes a new head; a run that failed, was cancelled or had its watch interrupted before its checks passed keeps it changing, "without passed checks".
EDI reads the request's draft flag from the forge each time it watches the open requests and each time the request's pipeline run changes, so a request marked ready for review is seen out of draft.
Every surface shows a changing request as "Changing, hold the merge" with why, never as ready for review, and the console gives it a red `changing` badge in place of the amber `review` badge.
`WaitingOnTheCaptain.count` counts each decision, review, changing request and board with questions of its own once; a board that only binds decisions counts as those decisions, and its line names no question count.
The snapshot is quiet only when nothing waits on the captain and nothing is ongoing.
A worker status of `needs-decision` or `blocked` is addressed to the primary (`StatusReport.isForThePrimary`), so the sitrep page shows its task as `with EDI` rather than waiting on the captain, and the acknowledged steer that resolves it settles it (`Worker.settledBy`, [steering](steering.md)).
`WaitingOnTheCaptain.workerQuestions` carries each such open question of an in-flight task's current worker with how long it has waited, outside `count`, so the turn report and the sitrep show it in the decision zone as waiting on EDI, and the sitrep page lists it there with a `with me` badge and the headline `1 question waiting on me` instead of `all clear` ([turn-report](turn-report.md)).
The sitrep page reads one document, `edi-sitrep.v1`, from `SitrepConsolePresenter`: the cards, the recent answers, at most ten landed and twenty charted tasks with their totals, the machine, the address word, the zone and the presence, so a 2 MB backlog gives a document of a few kilobytes.
Each ongoing task (`ConsoleSitrep.Ongoing`) carries its work key from its intake as its own `key` field, null for an unkeyed task, and a title with a leading copy of that key taken off, together with the separator after it (spaces, a colon, a bar or a dash); the key counts as leading only when it ends where the title's word ends, so `EDI-31` never takes the start off a title beginning with `EDI-310`, and the match ignores case.
The console's `OngoingList` shows the key first in the mono face and the ion colour, then the title, and an unkeyed task shows its title alone, with no placeholder.
The primary passes what its turn did as a small JSON document on standard input to `edi report`, which merges it with the snapshot ([captain-report](captain-report.md)).

## Why

One query keeps every surface consistent, separate presenters keep each format testable on its own, and large data travels through standard input and HTTP bodies, never arguments.
A work key in its own field lets the page style it apart from the title, and taking a leading copy off the title in the core means the key is never shown twice, whatever the console does.
The captain finds everything that needs them in one place on every surface, and no surface says "nothing open" while something waits.
A done reported inside a turn can run ahead of a steer that arrives only when the turn ends, a pipeline still running can push after the done, and a worker back at work after a steer changes the request again, so a request is ready only once its done stands and nothing still changes it.

## Alternatives rejected

- A separate query or overview command per surface - duplicated state assembly, the old arrangement, and two views that can disagree.
- Sorting in each presenter - the console, the brief and the report would drift apart.
- Keeping a blocked task's decision only under PARKED - the captain then misses it.
- Leaving a board's decision among the cards - the captain reads its full card in the report beside the board he answers it on.
- Each presenter working out which decisions a board binds - the report, the console and the briefs would drift apart.
- Gluing the work key into the ongoing title in the core - the page could no longer style the key, and every other surface reading the title would inherit the prefix.
- Leaving the work key out when the title already starts with it - the same task would show its key styled or unstyled depending on how it was titled.
- A work key on every block of the sitrep page - the captain asked for the Ongoing block only.
- Leaving a changing request out of the zone - the captain would see an open request with no warning, and the forge lets him merge it.
- Leaving a changing request out of the count - the page would call the captain all clear beside a request he must hold.

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
- 26/10/2026: a request was merged while a steer its worker had not yet received was still changing it, so a handed-over request shows as changing, not ready, while its pipeline runs, a steer waits unacknowledged or its worker is back at work after an accepted done (EDI-53).
- 03/11/2026: a worker's question to the primary showed only as an ongoing task's `with EDI`, so it went unseen for an hour; the open questions joined the decision zone as waiting on EDI (EDI-154).
- 09/11/2026: every finished request read as changing for up to an hour while its pipeline only watched its green CI for the merge, so the captain either waited or merged past the sitrep; a CI watch on passed checks no longer counts as changing the request, a handed-off or interrupted run counts as ended, a run that ended without passed checks is read again until it no longer changes the request, and a request reads as ready only out of draft on passed checks (EDI-238).
