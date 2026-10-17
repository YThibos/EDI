# ADR-098: The fleet ledger keeps the old harness's record contract for the time-booking tool

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The time-booking tool reads the old harness's activity ledger, one JSON record per line, for each task's ticket keys and its done moment, and books worklogs from it; section 8.9 has EDI keep that contract so booking continues across the cutover, add the work key to `task.dispatched`, and write only on a machine whose profile says `time.fleetLedger: true`.
Section 8.9 lists a `task.ready` event, while the old harness writes `task.pr_ready` with a `pr` member, and its documented contract lists exactly `task.dispatched`, `task.status`, `task.pr_ready`, `task.merged` and `task.cleaned_up`.

**Decision:** `EmitFleetLedger` follows the event log (ADR-096) and `FleetLedger` appends to `$EDI_HOME/state/fleet-ledger.jsonl` records of the form `{"v":1,"ts":<epoch>,"event":...,"task":...}`:
- `task.dispatched` with `kind`, `project`, `harness` (`claude`), `model` and the added `key`, for a task's first worker only, as the old harness records no relaunch;
- `task.status` with `state`, `key` and `text`, the text capped at 2,000 characters, for every status line;
- `task.pr_ready` with `pr`, the draft's full URL, when a ship's draft is recorded or opened;
- `task.merged` with `via` `pr` and `pr`, when the draft's merge is followed;
- `task.cleaned_up`, when the task is cleaned up.
`ts` is the time of the event, not of the writing, so a record written by a later round still books at the right moment; with the ledger off, followed events are passed over and never written later.

**Motivation:** Section 8.9's purpose is that booking keeps working unchanged, so where its list and the contract it names differ, EDI follows the contract: the old harness's own event name and members.
Section 8.9 says EDI emits "the event types the fork emits", and the old harness's own ledger writes `task.pr_ready` with a `pr` member, so the list's `task.ready` is a shorthand slip.
The time-booking tool reads neither name: it reads only the `task.status` text, the done states, `task.merged` and `task.cleaned_up`.

**Alternatives considered:**
- `task.ready` as section 8.9 lists it - a reader of the documented contract would not know it, and nothing gains from the new name.
- Writing the ledger inside each use case - every command would need to know about time booking.

**Consequences:**
- Good: the time-booking tool reads EDI's ledger with its existing reader, and its only change, made in its own repository, is reading a second ledger path and prefix.
- Bad: a record repeats after a crash between writing it and moving the follower's cursor, which the reader already tolerates.

**Revisit when:** The time-booking tool reads EDI's store directly, or the contract gains a version 2.

**Sources:** EDI rewrite specification, sections 7.1, 8.9 and 14.3; ADR-096; `application.ledger.EmitFleetLedger`, `adapter.ledger.FleetLedger`, `LedgerEntry`, `adapter.home.TimeSettings`, `MachineProfile`, `EdiHome.fleetLedger`.
