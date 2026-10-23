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
The answer settles the ticket outside the transaction: an existing ticket keys the task; a new one is created after the attempt is recorded, then filled; an interrupted creation first adopts an open ticket with exactly the drafted summary; a refused choice raises the same decision again; an unreachable tracker is retried every supervision round.
`FollowTickets` follows the event log for tickets that are not evergreen: the first dispatch adds the ticket to the one active sprint, a recorded draft is linked once under its address, and a followed merge walks the ticket to `mergedStatus`, never further, so a story stays open until the captain closes it.
`EmitFleetLedger` appends the old record contract to `state/fleet-ledger.jsonl` while `time.fleetLedger` is on: `task.dispatched` with the key, `task.status`, `task.pr_ready`, `task.merged` and `task.cleaned_up`, each stamped with its event's time.

## Why

The rules that decide which ticket work books on live in the domain and the use cases, and only the adapters know a tracker's wire format, so the same use cases serve both machines.
Every answer ends with the task keyed on exactly one ticket or with the question back before the captain.
Keeping the old ledger's own event names lets the time-booking tool read EDI's ledger with its existing reader.

## Alternatives rejected

- JIRA's Java client library - a large dependency with reflection for the eight calls EDI makes.
- A workflow walk hard-coded to one JIRA instance - would name an employer's workflow in tracked code.
- Creating the ticket at intake and asking afterwards - against asking first.
- Writing the ledger inside each use case - every command would need to know about time booking.

## Consequences

- Good: a new tracker, such as the future work board, is one adapter and one registry word, and a renewed token takes effect at the next call.
- Bad: intake on a JIRA project fails while JIRA cannot be reached, the profile must list the workflow's statuses, and a ledger record can repeat after a crash, which its reader tolerates.

## When to revisit

When EDI talks to a second external tracker, JIRA retires REST version 2, or the time-booking tool reads EDI's store directly.

## How we got here

- 17/10/2026: slice S6 built the tracker port, keying at intake, ticket follow-ups, hidden secrets and the fleet ledger, choosing the old harness's `task.pr_ready` over the specification's shorthand `task.ready`.
