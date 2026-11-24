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
A slice that is not eligible names what holds it back, worded as the backlog words it: its hold, and each unmet wait of its task outside the plan, such as the scout a research-first dispatch queued for it.
The backlog, `edi task show` and the console name only the waits not yet met, so a task whose waits are all met is never shown as waiting.
`V29__plans_and_their_waits.sql` adds the `plan` and `plan_slice` tables; a slice's wait refers to an earlier slice of the same plan by a foreign key and a check.
Each supervision round, after the deferred dispatches, `DispatchEligibleSlices` dispatches the plan's slices itself, so nobody dispatches a slice by hand ([supervision](supervision.md)).
It takes every plan that still has a queued slice, oldest first, and its eligible slices in their order, and dispatches each through the same `DispatchTask` as a dispatch by hand, with the slice's category, while three things hold: the plan has a free place, its project has room for another worker, and the captain's presence allows one more (`RoomForAWorker`, [dispatch-routing](dispatch-routing.md)).
A plan's free places are its capacity less the slices that take a place: those WORKING or CHANGING, and those dispatched but still QUEUED, deferred or waiting on their own research; a slice frees its place once it is delivered and takes it again while it is changed.
A slice dispatched before, such as a ship whose research went first, keeps the category of its dispatch.
Dispatch stays serialised in `DispatchTask`, so the round and the primary never choose a branch number at once ([work-keys-and-branches](work-keys-and-branches.md)).
A plan dispatches itself only when its go cites a stored word of the captain (`PlanGo`, `CaptainsGo`), checked in code each round: a decision written as its number, such as D44, that the captain answered with an option that authorises promote, or a message the captain typed, cited with `"message": <n>` when the plan is entered and stored with its session (`V36__a_plan_cites_its_go.sql`).
A plan whose go cites neither dispatches nothing, and the primary is woken once with why; that go covers every slice, so the core asks no word of the captain per slice.
This check was settled while the captain was away, and he can change it.
Each slice the round dispatches records `SliceDispatched` and wakes the primary once, telling it which worker took the slice up, or that its research goes first, so it dispatches nothing for it.
A slice the round cannot dispatch records `SliceNotDispatched` with the refusal and wakes the primary; the round tries again each time, gives the slice's place to the next eligible slice meanwhile, and wakes the primary again only when the reason changes or after the core restarts.
The effort ceiling stays high for every slice; one effort word honoured for every slice of a plan is EDI-90.

## Why

The order of a plan's work is stored once, beside the tasks it orders, so nobody keeps it in notes or in the primary's head.
A slice can wait for a merge rather than a cleanup, which lets the next slice start as soon as the work it builds on is in the base branch.
One list of waits, read the same way by dispatch, the backlog and the reports, means a wait cannot be honoured in one place and ignored in another.
Entering the whole plan in one call and one transaction leaves either every slice queued with its wait or nothing at all.
The captain chose Q9 A: the core dispatches the next slice once its wait is met and the plan has room, so a plan costs one go instead of one captain prompt and one primary turn per slice, and its order no longer depends on the primary remembering it.

## Alternatives rejected

- A wait on any task, as `--blocked-by` - the plan's order is in slice numbers, and a slice waiting outside its plan would make the plan's order something no plan shows.
- Several waits per slice - the captain chose one wait per slice (Q8 A), and the plans so far needed no more.
- Storing a slice's wait as a blocker of its task - a wait on a merge is not a blocker, and closing the awaited task would erase the plan's recorded order.
- Waking the primary each round a slice's dispatch is refused - the same refusal would reach it every 30 seconds.
- Holding a slice's place while its dispatch is refused - one slice that cannot start would stall every later slice of its plan.
- Trusting the go as the line of text the plan was entered with - authority is checked in code against the captain's stored word, never taken from a line any session can write.
- A plan id of its own - the umbrella key already names the plan everywhere the captain reads it, such as "the EDI-52 plan".
- A wait on a dropped slice that is never met - the plan would stall with no command to change the wait, and a closed task blocks nobody elsewhere in EDI.

## Consequences

- Good: `edi plan show` answers what can start next without reading notes or the forge.
- Good: the primary's by-hand plan habits, queueing the next step before a merge and reading the forge for merge order, expire with this work.
- Good: a plan dispatches itself, so the captain's go on it is the last prompt it needs, and the primary only hears of each slice as it starts.
- Bad: a slice whose dispatch is refused keeps being tried each round until its cause is put right or the slice is held.
- Bad: a plan entered with a go that cites no stored word of the captain never dispatches itself, and as a plan cannot yet be changed, its slices are dispatched by hand or the plan is entered again.
- Bad: a slice's wait and a task's blockers are stored in two tables and joined when they are read.
- Bad: a plan cannot yet be changed once entered; a wrong slice is dropped and entered again outside the plan.

## When to revisit

When a slice needs to wait for more than one earlier slice, or a plan needs to change its slices after it is entered.

## How we got here

- 26/10/2026: the EDI-74 scout found plans kept in notes and in the primary's head, and proposed plans as a first-class domain object; the captain chose Q8 A, Q9 A and Q10 A.
- 13/11/2026: plans, slices and their waits entered the domain and the store, with `edi plan add` and `edi plan show`, and `--blocked-by` became "after landed" (EDI-88).
- 24/11/2026: the supervision round began dispatching each eligible slice within the plan's and the project's capacity and the captain's presence, and waking the primary with each, only under a go that cites the captain's stored word; a slice dispatched but still queued keeps its place (EDI-89).
