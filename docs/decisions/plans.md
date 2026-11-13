# A plan is a titled set of slices, each a task with its own key and one wait on an earlier slice's stage

**Kind:** Design

## Context

Until now the only plan in EDI's domain was research first: one scout, one ship waiting for it ([research-first](research-first.md)).
The EDI-52 plan was twenty tasks titled by hand with their order in their notes, none carried a dependency, and its first three slices were dispatched together with the order held in the primary's head.
A registered project's plan of eleven slices kept its order in prose only.
`--blocked-by` waited for the blocker to be cleaned up, so it could not say "after it is merged".
Each slice needs its own key, because tasks that share a key share a branch ([work-keys-and-branches](work-keys-and-branches.md)).
The captain chose Q8 A in round 1 of the EDI-74 research: each slice names its wait, after N merged by default, after N landed, or nothing.

## Decision

`domain/plan/Plan` is a `PlanHeading` and its slices.
The heading holds the plan's title, its umbrella work key, which is the plan's name, the project its slices work on, the report it came from, the decision that gave the go, its capacity, the slices working at once, and, when the captain gives it, an effort word.
A `Slice` is a task with its number in the plan, its dispatch category and one `SliceWait`: `After` a slice number reaching an `AwaitedStage`, MERGED or LANDED, or `None`.
`PlanDraft` is the plan as it is entered: it orders the slices by number, gives a slice that names no wait "after N merged" on the slice before it, and refuses a number used twice, a key shared by two slices and a wait on a slice that is not earlier in the plan.
`domain/task/Wait` is any wait of one task on another, read against the stages of [task-lifecycle](task-lifecycle.md): it names the task waited for, the stage awaited and the stage that task is at, and it is met once that task reaches the stage awaited or closes, since a closed task blocks nobody ([task-intake](task-intake.md)).
`--blocked-by` is "after landed" on the named task (`Wait.afterLanded`), which is what it meant: its blocker was waited for until it was cleaned up, and every ship that is cleaned up has landed first.
`TaskStore.waitsOf` returns a task's waits, those on its blockers and the one its plan gives it, and `DispatchTask`, `LaunchWorker`, the backlog, `edi task show` and the fleet snapshot all read that one list, so a slice whose wait is not met is not dispatched by hand either.
`SliceStanding` is a slice with its task and the waits `TaskStore.waitsOf` returns for it, and `isEligible` says the slice may start: its task is queued, not held, and every wait is met.
`edi plan add --from -` takes the plan as JSON on standard input (I-36), with per slice its number, title, category, key, body and wait; `AddPlan` queues each slice as a ship through `AddTask`, so every intake rule of a task holds for a slice, and stores the plan and a `SlicePlanned` event on each slice in one transaction.
`AddPlan` refuses a second plan on the same umbrella key and a capacity above the project's own (`task.plan-intake`); a slice's category is checked against the dispatch rules when the slice is dispatched.
`edi plan show <key>` prints the heading and each slice with its task, key, stage and wait, and marks the eligible slices.
A slice that is not eligible names each unmet wait of its task outside the plan, such as the scout a research-first dispatch queued for it, worded as the backlog words a wait.
`V29__plans_and_their_waits.sql` adds the `plan` and `plan_slice` tables; a slice's wait refers to an earlier slice of the same plan by a foreign key and a check.
Entering a plan dispatches nothing: the plan dispatching its eligible slices within its capacity is EDI-89, and one effort word honoured for every slice is EDI-90.

## Why

The order of a plan's work is stored once, beside the tasks it orders, so nobody keeps it in notes or in the primary's head.
A slice can wait for a merge rather than a cleanup, which lets the next slice start as soon as the work it builds on is in the base branch.
One list of waits, read the same way by dispatch, the backlog and the reports, means a wait cannot be honoured in one place and ignored in another.
Entering the whole plan in one call and one transaction leaves either every slice queued with its wait or nothing at all.

## Alternatives rejected

- A wait on any task, as `--blocked-by` - the plan's order is in slice numbers, and a slice waiting outside its plan would make the plan's order something no plan shows.
- Several waits per slice - the captain chose one wait per slice (Q8 A), and the plans so far needed no more.
- Storing a slice's wait as a blocker of its task - a wait on a merge is not a blocker, and closing the awaited task would erase the plan's recorded order.
- A plan id of its own - the umbrella key already names the plan everywhere the captain reads it, such as "the EDI-52 plan".
- A wait on a dropped slice that is never met - the plan would stall with no command to change the wait, and a closed task blocks nobody elsewhere in EDI.

## Consequences

- Good: `edi plan show` answers what can start next without reading notes or the forge.
- Good: the primary's by-hand plan habits, queueing the next step before a merge and reading the forge for merge order, expire with this work.
- Bad: a slice's wait and a task's blockers are stored in two tables and joined when they are read.
- Bad: a plan cannot yet be changed once entered; a wrong slice is dropped and entered again outside the plan.

## When to revisit

When a slice needs to wait for more than one earlier slice, or a plan needs to change its slices after it is entered.

## How we got here

- 26/10/2026: the EDI-74 scout found plans kept in notes and in the primary's head, and proposed plans as a first-class domain object; the captain chose Q8 A, Q9 A and Q10 A.
- 13/11/2026: plans, slices and their waits entered the domain and the store, with `edi plan add` and `edi plan show`, and `--blocked-by` became "after landed" (EDI-88).
