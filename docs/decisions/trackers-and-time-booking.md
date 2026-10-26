# The tracker port has three adapters, the captain chooses each ticket, and the fleet ledger keeps the old booking contract

**Kind:** Architecture

## Context

`Tracker` is one of the six ports, because JIRA on the work laptop and EDI's own keys elsewhere are two production implementations ([ports-and-architecture-rules](ports-and-architecture-rules.md)).
Every block of work lands on a ticket (I-08), no ticket is created without the captain's answer, and every field is filled after creation.
A tracker can be unreachable, refuse a call, or a crash can fall between creating a ticket and recording it, and none of these may create a second ticket.
Secrets are read at call time and never printed (I-32).
The time-booking tool books worklogs from the old harness's activity ledger, one JSON record per line, and must keep working across the cutover.

## Decision

`Tracker` offers `kind`, `findExisting`, `create`, `fill`, `verified`, `transition`, `addToCurrentSprint` and `link`.
Each registry entry names its tracker with `tracker.kind` (`jira`, `edi` or `none`), and `Trackers` refuses a project on an external tracker the machine profile does not name.
The profile's `tracker` section holds everything a JIRA instance differs in, such as its URL, credentials, evergreen tickets, workflow statuses, merged status and issue type.
`JiraTracker` speaks REST version 2 and the agile API through `JiraCalls`, with the proxies the environment names (`EnvironmentProxies`); `EdiNativeTracker` issues keys and keeps no tickets; `NoTracker` issues none.
`JiraCalls` reads the token from the keys file at every call, sends it only in the `Authorization` header, and writes `[secret]` for it in any message (`KeysFile`).
At intake `AddTask` asks `TaskKeys`: a given key must belong to the project and exist; unkeyed work on an EDI-native project gets the next key; on JIRA, `TicketProposals` raises a decision with up to three matching tickets and a drafted new one.
The answer settles the ticket outside the transaction: an existing ticket keys the task; a new one is created after the attempt is recorded, then filled; an interrupted creation first adopts an open ticket with exactly the drafted summary; a refused choice raises a new decision on the task with the refusal in its context, the answered one kept ([captain-decisions](captain-decisions.md)); an unreachable tracker is retried every supervision round.
`FollowTickets` follows the event log for tickets that are not evergreen: the first dispatch adds the ticket to the one active sprint, a recorded draft is linked once under its address, and a followed merge walks the ticket to `mergedStatus`, never further, so a story stays open until the captain closes it.
`WorkedTime` measures a task's active time over its timeline: `WorkedTimes` reads the event log into `WorkMark`s, where a first dispatch starts the wall clock, a working or resolved status, a received steer, a relaunch or a resume typed after a usage limit starts a stretch, and the next mark of any other kind ends it, whether a non-working status, a decision raised, a hold, a fresh dispatch, a usage limit reached, a stop on an error, or the worker's session end or death.
A stretch still open runs up to the moment measured, which is the task's closing or now; `edi task show` prints "Worked: active <h>h<mm>m of <h>h<mm>m" against the wall clock since the first dispatch.
`EmitFleetLedger` appends the old record contract to `state/fleet-ledger.jsonl` while `time.fleetLedger` is on: `task.dispatched` with the key, `task.status`, `task.pr_ready`, `task.merged`, then at cleanup `task.worked` with the key and `active_minutes` measured up to the cleanup, and `task.cleaned_up`, each stamped with its event's time.

## Why

The rules that decide which ticket work books on live in the domain and the use cases, and only the adapters know a tracker's wire format, so the same use cases serve both machines.
Every answer ends with the task keyed on exactly one ticket or with the question back before the captain.
Keeping the old ledger's own event names lets the time-booking tool read EDI's ledger with its existing reader.
Measuring from the event log, which already records every status with its time, books the time a worker worked and leaves out the time the task waited on the captain, which the worker's own estimate in its report includes.

## Alternatives rejected

- JIRA's Java client library - a large dependency with reflection for the eight calls EDI makes.
- A workflow walk hard-coded to one JIRA instance - would name an employer's workflow in tracked code.
- Creating the ticket at intake and asking afterwards - against asking first.
- Writing the ledger inside each use case - every command would need to know about time booking.
- The worker's own `worked:` line - a wall-clock estimate that counts the waits on the captain.
- A new store table of active stretches - the event log already holds every status change, so a second record could only drift from it.

## Consequences

- Good: a new tracker, such as the future work board, is one adapter and one registry word, and a renewed token takes effect at the next call.
- Good: the measure leaves out every idle stretch EDI notices, such as a decision, a hold, a usage-limit park, an error stop or a session end, and counts the work a worker resumes on a message, a relaunch or a limit resume.
- Bad: a worker that goes idle without EDI noticing keeps its stretch open until its next mark, so its idle time counts as active.
- Bad: intake on a JIRA project fails while JIRA cannot be reached, the profile must list the workflow's statuses, and a ledger record can repeat after a crash, which its reader tolerates.

## When to revisit

When EDI talks to a second external tracker, JIRA retires REST version 2, or the time-booking tool reads EDI's store directly.

## How we got here

- 17/10/2026: slice S6 built the tracker port, keying at intake, ticket follow-ups, hidden secrets and the fleet ledger, choosing the old harness's `task.pr_ready` over the specification's shorthand `task.ready`.
- 22/10/2026: a refused ticket choice is asked again as a new decision, since an answered decision is never rewritten ([captain-decisions](captain-decisions.md), EDI-40).
- 26/10/2026: EDI measures each task's active minutes from its timeline, shows them in `edi task show` and books them as `task.worked` at cleanup (EDI-54).
