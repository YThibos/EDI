# ADR-158: A closed task blocks nobody

**Date:** 21/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** A task names the tasks it waits for (`task_dependency`), and ADR-046 counted a blocker as finished only when it was done.
A dropped blocker therefore blocked its dependants for good: in the shadow run the report still said a task was "blocked by" a task the captain had dropped.
A task closes in three places: `DropTask` on the captain's word, `CleanUpTask` when its work landed, and `AnswerDecision` when a captain item's decision is answered.

**Decision:** Closing a task takes it out of every other task's blocked-by in the same transaction: each of the three use cases calls `DependantTasks.stopWaitingFor`, which updates every task that waits for the closed one with `Task.noLongerWaitingFor`.
The migration `V9__waiting_on_the_captain.sql` removes the dependencies on tasks that were already done or dropped.

**Motivation:** A dropped task no longer stands in anyone's way, and a task's blocked-by names only work that is still open, so the report, the backlog and dispatch read the same list.

**Alternatives considered:**
- Counting a dropped blocker as resolved and keeping the row - every reader would have to know to skip it, and the backlog would keep naming it.
- Refusing to drop a task others wait for - the captain dropped it because it no longer matters, so its dependants should not wait for it.

**Consequences:**
- Good: a task never shows "blocked by" a closed task, and its dispatch is no longer refused for one.
- Bad: once a task closes, its dependants no longer record that they once waited for it.

**Revisit when:** A dependant needs to know which blocker it waited for, such as to read that blocker's report.

**Sources:** EDI rewrite specification, section 8.1; ADR-046, ADR-117, ADR-156; `domain.task.Task`, `application.task.DependantTasks`, `application.delivery.DropTask`, `CleanUpTask`, `application.decision.AnswerDecision`, `adapter.store.TaskStore`.
