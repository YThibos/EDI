# A task enters the queue through registry-checked intake, and a closed task blocks nobody

**Kind:** Design

## Context

A task id is a kebab-case slug, unique per home and never reused.
An unregistered project cannot receive a ship, but tasks migrated from the old harness can name no project at all, and the migration lists them instead of dropping them.
The project registry, `config/projects.yaml`, holds each project's machine-specific settings: clone, forge, account, base, delivery mode, tracker, branch kinds, capacity and craft rules.
A task names the tasks it waits for, and a task closes when it is dropped, cleaned up, or when a captain item's decision is answered.

## Decision

`AddTask` applies the intake rules, and the domain's `Intake` demands only what every task must satisfy: a title and no repeated project or blocker.
The id is the one given with `--id`, refused when taken, or a slug of the title (`TaskId.sluggedFrom`), numbered `-2`, `-3` onwards when taken.
A ship or a scout must name at least one registered project, a captain item may name none, every blocker must exist and be open, and the work key is settled at intake, or given once afterwards with `edi task key` to a task that has none ([trackers-and-time-booking](trackers-and-time-booking.md)).
`ProjectRegistry` reads `projects.yaml` with Jackson's YAML module on every use, maps only the fields its callers need, and refuses an unreadable file or an unusable name as `UnreadableRegistry`, naming the file; the store keeps no project table.
Closing a task takes it out of every other task's blocked-by in the same transaction: `DropTask`, `CleanUpTask` and `AnswerDecision` call `DependantTasks.stopWaitingFor`.

## Why

The primary can add a task in one line, a readable id comes for free, and the rules that depend on the registry live where the registry is read, not in the domain.
The registry stays a file the captain can read and edit, with one source for every project setting, and an edit takes effect at the next command.
A dropped task no longer stands in anyone's way, so the report, the backlog and dispatch read the same list.

## Alternatives rejected

- Generated ids such as a counter - unreadable in reports and branch names.
- The project rule in `Intake` - would make the migrated backlog unrepresentable.
- The registry as a store table, or read once at start-up - a second source for machine configuration, or an edit that needs a restart.
- Counting a dropped blocker as resolved and keeping the row - every reader would have to know to skip it.

## Consequences

- Good: the backlog keeps every task the old harness had, and new work is still checked at intake.
- Bad: a task's projects and blockers cannot be changed after intake, each command that checks projects parses a small file, and dependants no longer record which closed task they once waited for.

## When to revisit

When the primary needs to change a task's projects or blockers after intake, or a key it already has, or a project setting must change transactionally with the store.

## How we got here

- 15/10/2026: slice S1 built intake and read the registry as configuration.
- 21/10/2026: a shadow run showed a task "blocked by" one the captain had dropped, so closing a task now releases its dependants, and `V9__waiting_on_the_captain.sql` removed the stale dependencies.
- 04/11/2026: an unkeyed task can be given its work key after intake with `edi task key`, which closes its open ticket decision (EDI-179).
