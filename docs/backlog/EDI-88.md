# EDI-88 Plans and their waits

| Field | Value |
|---|---|
| Key | EDI-88 |
| Kind | ship, a change to the repository, on branch `feature/EDI-88` |
| Wave and order | wave 3, order 7 |
| Source | EDI-74 plan, step 17 |
| Size | M |
| Blocked by | [EDI-85](EDI-85.md) |
| Status | open |
| Cloud session | ok |

## Goal

A plan becomes a first-class domain object: a titled set of slices, each a task with its own key, its number in the plan and one wait, so an order that lived in task notes and the primary's head is stored and shown.

## Context

Today the only plan in EDI's domain is research first: one scout, one ship waiting for it.
The EDI-52 plan is twenty tasks titled by hand with the order in their notes, none carries a dependency, and its first three slices were dispatched together with the order held in the primary's head.
`--blocked-by` waits for the blocker to be DONE, which means cleaned up; it cannot say "after it is merged".
Capacity exists per project, not per plan.
The research is in [the EDI-74 plan](research/EDI-74-plan.md), section 7.

## Acceptance criteria

- The plan domain and its store: a plan has a title, the umbrella work key, the report it came from, the decision that gave the go, its capacity (slices working at once, within the project's own) and, when the captain gives it, an effort word.
- A slice is a task with its own key, its number in the plan and one wait: "after N merged" (the default, on the slice before), "after N landed", or none, read against the stages in [task-lifecycle](../decisions/task-lifecycle.md).
- `edi plan add --from -` takes the plan as JSON on standard input, with per slice its number, title, category, key, body and wait; `edi plan show <plan>` prints the slices with their stage and wait.
- `--blocked-by` becomes "after landed" on the named task, which is what it means now.
- A plan of three slices where slice 2 waits on slice 1 merged and slice 3 on nothing shows slices 1 and 3 eligible, then 2 once 1 is MERGED.
- A new decision record named `plans` in the decision log records the decision, and the lifecycle record's QUEUED to WORKING transition cites it.

## Requirements from decisions and answers

- Q8 A (EDI-74 round 1): each slice names its wait: after N merged (the default), after N landed, or nothing.
- Fleet data goes in on standard input, never as a large argument (I-36).
- This item does not dispatch anything; dispatching eligible slices is EDI-89.
- Each slice needs its own key, because tasks sharing a key share a branch.
- Pure domain first: the plan and its waits live in the domain with no Spring, input, output or process, as `ArchitectureRulesTest` requires.
- A new store table needs a Flyway migration; this item touches resources and the command, so the native quick build is part of the verification.
- It retires the primary's by-hand plan habits: the learnings about queueing the next step before a merge and about reading the forge for merge order expire with the plan work.

## Research and measurements

- The EDI-52 plan: twenty slices keyed EDI-52 to EDI-73, none with a recorded dependency.
- The SAP roundtrip plan: eleven slices, the order only in prose.
- The effort word was said once for eleven slices but held per task.

## Read first

- [task-lifecycle](../decisions/task-lifecycle.md), [research-first](../decisions/research-first.md), [task-intake](../decisions/task-intake.md), [store](../decisions/store.md).
- `edi-core/src/main/java/io/github/ythibos/edi/domain/task/Blocker.java`, `application/task/DependantTasks.java`, `application/task/AddTask.java`, `application/dispatch/DispatchTask.java`.
- `edi-cli/src/main/java/` for how a command is added, and `edi-core/src/main/java/io/github/ythibos/edi/web/task/`.

## How to verify

- `./mvnw verify`, then `./mvnw -Pnative,quick-build verify`.
- The three-slice plan test above, through the command and the core's API.
- `PortInventoryTest` still counts six ports.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status and the index row updated in the same pull request.
