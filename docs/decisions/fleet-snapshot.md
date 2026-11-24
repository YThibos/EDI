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
- `changing`: every such request that is still a draft, whose pipeline run still changes it, whose head the forge shows with failed checks or whose inbox still holds an unacknowledged steer, and every open request of a task in flight whose event log holds an accepted done its latest worker no longer stands by (`ChangingRequest`), with why it is changing (`ChangingRequest.whyChanging`: "worker back at work", "still a draft", the running pipeline's step or the step a failed run stopped in, "checks failing on its head", and the steers' numbers); a first-pass draft whose task never had a done accepted is in neither list.

Then come `ongoing` (in-flight tasks nothing holds, each an `OngoingTask` with its latest worker, pipeline run and delivery), `parked` (every held task except one that waits only for its decision, with its D number and the open tasks it waits for, `ParkedTask.whyParked`), and `queued`, each in the store's row order, plus the tasks landed in the last seven days and the answers of the last 24 hours.
EDI keeps observing the pipeline of every task a worker still serves, before and after its done (`ObservePipelines`), so a run started after the done is recorded and a run is seen to finish.
A handed-over request's latest run still changes it (`PipelineRun.stillChanges`) while that run runs, unless its only running step is the CI watch on checks the pipeline has seen pass (`PipelineRun.checksPassed`).
The pipeline's last step keeps watching CI after the checks pass, until the request merges, and re-arms each time the base advances, so a request whose checks passed reads as ready while that watch runs; it reads as changing again when the watch sees CI on the head fail or pend again, starts a fix, or pushes a new head.
Once a run has ended it no longer changes the request, whatever its CI step ended in: passed, failed, cancelled, interrupted, handed off, or skipped on a project whose CI does not run ([pipeline-steps](pipeline-steps.md)).
The one exception is a run that failed in a step of its own, such as its review or its tests: `AxiStatus` reads the first row of the run's `steps` table that neither completed nor was skipped as the step it stopped in (`PipelineRun.step`), and a run that ended short of completion in any step but `ci` keeps the request changing, "pipeline failed in its test step", until a later run passes it or the forge shows passed checks on the request's head (`Delivery.checks`).
A run whose own step was ended by a shutdown, crash or restart of the pipeline's service, which `AxiStatus` reads from the run's `error` line (`PipelineRun.stoppedByThePipelineService`), failed through no fault of the request and does not hold it.
A handed-over request whose head the forge shows with failed checks reads as changing, "checks failing on its head", whatever its last run did, so CI that turns red after the done and that the pipeline's watch gave up fixing holds the merge; checks the forge shows as pending, or no checks at all, hold nothing once no run watches them (`HeadChecks`: passed, failed or undecided).
The forge adapters read those checks with every view of the request ([forge-adapters](forge-adapters.md)), and the open drafts' watch keeps them current ([merge-following](merge-following.md)).
EDI keeps the request's draft flag as the forge shows it ([merge-following](merge-following.md)), so a request marked ready for review is seen out of draft.
Every surface names the target of a request that is not the base, as `targets <branch>, not the base: merging it does not land the work`, on its review or changing line and on its ongoing task, and shows an ongoing task whose request merged into another branch as not landed (`WaitingWords`, [merge-following](merge-following.md)).
Every surface shows a changing request as "Changing, hold the merge" with why, never as ready for review, and the console gives it a red `changing` badge in place of the amber `review` badge.
`WaitingOnTheCaptain.count` counts each decision, review, changing request and board once, a board together with every decision it binds as one, because the captain answers them in one place; a board that only binds decisions names no question count.
The sitrep page counts the same way and deals a card only for a decision that no board on the page binds, so paging through its cards never shows a D number twice.
The snapshot is quiet only when nothing waits on the captain and nothing is ongoing.
A worker status of `needs-decision` or `blocked` is addressed to the primary (`StatusReport.isForThePrimary`), so the sitrep page shows its task as `with EDI` rather than waiting on the captain, and the acknowledged steer that resolves it settles it (`Worker.settledBy`, [steering](steering.md)).
`WaitingOnTheCaptain.workerQuestions` carries each such open question of an in-flight task's current worker with how long it has waited, outside `count`, until a steer that resolves its key is sent, acknowledged or not (`FleetSnapshotQuery`), so the turn report and the sitrep show it in the decision zone as waiting on EDI, and the sitrep page lists it there with a `with me` badge and the headline `1 question waiting on me` instead of `all clear` ([turn-report](turn-report.md)).
The sitrep page reads one document, `edi-sitrep.v1`, from `SitrepConsolePresenter`: the cards, the recent answers, at most ten landed and twenty charted tasks with their totals, the machine, the address word, the zone and the presence, so a 2 MB backlog gives a document of a few kilobytes.
Each ongoing task (`ConsoleSitrep.Ongoing`) carries its work key from its intake as its own `key` field, null for an unkeyed task, and a title with a leading copy of that key taken off, together with the separator after it (spaces, a colon, a bar or a dash); the key counts as leading only when it ends where the title's word ends, so `EDI-31` never takes the start off a title beginning with `EDI-310`, and the match ignores case.
The console's `OngoingList` shows the key first in the mono face and the ion colour, then the title, and an unkeyed task shows its title alone, with no placeholder.
The primary records what its turn did with `edi turn`, one item at a time or as a small JSON document on standard input, and the turn report and the sitrep merge the recorded items with the snapshot ([turn-report](turn-report.md)).

## Why

One query keeps every surface consistent, separate presenters keep each format testable on its own, and large data travels through standard input and HTTP bodies, never arguments.
A work key in its own field lets the page style it apart from the title, and taking a leading copy off the title in the core means the key is never shown twice, whatever the console does.
The captain finds everything that needs them in one place on every surface, and no surface says "nothing open" while something waits.
A done reported inside a turn can run ahead of a steer that arrives only when the turn ends, a pipeline still running can push after the done, and a worker back at work after a steer changes the request again, so a request is ready only once its done stands and nothing still changes it.
The worker marks its request ready for review only on green checks, or on a passed local validation where CI does not run, and EDI accepts its done only with the request at its last commit, so a run that ended without a CI result says nothing against the request; a step of the run's own that failed does, until a later run or the forge's checks outweigh it, unless the pipeline's service stopping ended that step.
Failed checks on the head say something about the work, so they hold; this follows from the captain's answer to D131, which keeps ready meaning checked while dropping holds that say nothing about the work, and pending checks that no run watches would otherwise hold for ever.

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
- Holding a request whose last run ended without passed checks - a project without CI, a head that skips CI and a watch ended by a restart never produce them, so finished requests read as "hold the merge" for days.
- Reading every ended run as ready - a run that failed its own review or tests after the done would pass for a finished request.
- Reading a run that ended in its `ci` step as ready whatever the forge shows - CI that turned red after the done would pass for a finished request.
- Holding pending checks as well as failed ones - checks that stay pending with no run watching them would hold the request for ever.
- Holding on a cancelled check - cancelling a check says nothing about the work, the same reason a stop of the pipeline's service holds nothing.
- Holding only on the checks the forge requires - the views the adapters read do not say which checks are required.

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
- 12/11/2026: a run started after a done went unrecorded, so EDI observes a served task's pipeline whatever its worker reported (EDI-85).
- 13/11/2026: the primary records its turn's items with `edi turn` as they happen, and the reports merge them with the snapshot in place of the turn document given to `edi report` (EDI-269).
- 13/11/2026: two open decisions, each bound to its own board, were counted as four and the sitrep page showed a bound decision again as a card of its own, so a board and the decisions it binds count as one and the page deals no card for a decision a board binds (EDI-274).
- 16/11/2026: the turn report listed as waiting on EDI worker questions the primary had already answered in the same turn, so a question stops waiting once its resolving steer is sent, not only once it is acknowledged (EDI-292).
- 17/11/2026: a finished request on a repository that runs no merge request pipeline read "Changing, hold the merge: pipeline cancelled without passed checks" for two days, so an ended run holds a handed-over request only when it failed in a step of its own, not through a stop of the pipeline's service, and the forge shows no passed checks on its head (EDI-265, D131).
- 18/11/2026: a run that ended in its `ci` step on red checks would have read as ready, so failed checks the forge shows on a handed-over request's head hold it, following from the answer to D131 (EDI-265).
- 24/11/2026: a request that targets another branch than the base names its target on every surface, and a request merged into another branch shows as not landed (EDI-244).
