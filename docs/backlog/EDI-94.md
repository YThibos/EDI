# EDI-94 Worklogs and stories through the tracker

| Field | Value |
|---|---|
| Key | EDI-94 |
| Kind | ship, a change to the repository, on branch `feature/EDI-94` |
| Wave and order | wave 1, order 3 |
| Source | EDI-74 plan, step 24 |
| Size | M |
| Blocked by | nothing |
| Status | folded: into [EDI-161](EDI-161.md), on the captain's word of 3 November 2026, which books the core's measured minutes and drives the ticket from the stage |
| Cloud session | ok |

## Goal

EDI books a finished task's active time on its ticket and creates a story when a ticket proposal is approved, both through the `Tracker` port, and drives the ticket's status from the task's stage, so no external script remains in EDI's procedures.

## Context

Today EDI moves JIRA tickets itself through `JiraTracker.transition` and the workflow in the machine profile, but it never books a worklog and never creates a story; the primary does both by hand with a shell script, which is the workaround behind two learnings.
EDI-54 (EDI-52 slice 3) has landed: a domain `WorkedTime` sums a task's active stretches, `edi task show` prints them, and cleanup writes a `task.worked` ledger record.
This item builds on it.
The research is in [the EDI-74 plan](research/EDI-74-plan.md), section 5.1 (lines 8 and 26) and step 24, and in [the EDI-52 plan](research/EDI-52-plan.md), finding F3.

## Acceptance criteria

- The `Tracker` port gains booking a worklog and creating a story; the JIRA adapter implements both, the EDI-native and no-tracker adapters refuse or do nothing as their kind says.
- At cleanup, a task keyed on a JIRA project books its active minutes on that ticket once; a task keyed `EDI-n` books them on the evergreen ticket named by the machine profile (`tracker.evergreen.tooling`), with the EDI key in the worklog comment.
- Against a stubbed JIRA, a cleaned-up task books its minutes once with the right comment, and a second cleanup attempt books nothing.
- A ticket proposal answered yes creates the story through the port.
- The ticket's status follows where the work runs, driven from the task's stage and never by hand:
  - an MR ready for the captain's review moves the ticket to "Test in DEV", which means he tests it on his own machine;
  - a merge into the project's main branch, which auto-deploys to the test server, moves it to "TEST in TST";
  - a later slice starting on a ticket already past that status moves it back to "In Progress".
- No `jira-sync.sh` call remains in EDI's skills, briefs or procedures.
- `docs/decisions/trackers-and-time-booking.md` gains a history line and states the status flow.

## Requirements from decisions and answers

- The captain (28 October 2026): JIRA status follows where the work runs; this flow is to be followed strictly whenever EDI manages the JIRA side of tasks, and the new stage on the task is the route for it.
  Drive these transitions from the task's stage: ready for review, merged, and a new slice started.
- The transition names and numbers are in the machine profile's workflow, not in code; read them at the moment of use.
  The captain's machine profile is not in the repository: build the tests on a fixture profile and a stub, and say so in the pull request.
- Secrets are read at call time by an adapter and never printed, logged or stored (I-32).
- Tracked files name no particular employer, host or ticket prefix: use invented names in tests and records.
- The stage is stored by EDI-85, which has not landed when this item runs in wave 1.
  Use the signals that exist today (a request ready for review, a merge followed, a dispatch on a key) behind one small decision method, so EDI-85 can switch it to the stored stage in one place.
  Say in the pull request which signals were used.

## Research and measurements

- Learnings 8 and 26 of the first week describe the same defect: the primary books worklogs and creates stories by shell script because the port has neither.
  They expire with this item.
- Active time is the sum of the stretches in working: a timeline working 10:00 to 10:30, needs-decision until 11:00 and working until 11:15 gives 45 minutes.

## Read first

- `edi-core/src/main/java/io/github/ythibos/edi/adapter/tracker/JiraTracker.java`, `JiraCalls.java`, `EdiNativeTracker.java`, `NoTracker.java`.
- `edi-core/src/main/java/io/github/ythibos/edi/application/port/` (the `Tracker` port) and `application/tracker/` (`FollowTickets`, `TicketProposals`, `TaskKeys`, `Trackers`).
- `edi-core/src/main/java/io/github/ythibos/edi/domain/task/WorkedTime.java`, `application/task/WorkedTimes.java`, `application/delivery/CleanUpTask.java`.
- [trackers-and-time-booking](../decisions/trackers-and-time-booking.md), [trackers-and-time](../trackers-and-time.md), [jira verification notes](../verification/jira.md), [ports-and-architecture-rules](../decisions/ports-and-architecture-rules.md).

## How to verify

- `./mvnw verify`; the core's tests run under both the laptop and desktop fixture profiles, so a booking must happen on the first and not on the second.
- Tests with a stubbed JIRA over the adapter's HTTP boundary, as the existing adapter tests do.
- The port count stays at six: add operations to the existing `Tracker` port, do not add a seventh port.
- No native build unless a resource or Spring configuration changes.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.
