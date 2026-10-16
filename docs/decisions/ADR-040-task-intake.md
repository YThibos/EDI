# ADR-040: How a task enters the queue

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** A task id is a kebab-case slug, unique per home and never reused (section 8.1); the command list gives `edi task add` no id argument.
An unregistered project cannot receive a ship (section 8.4), but tasks migrated from the old harness can name no project at all, and the migration lists them for the captain instead of dropping them (section 18).

**Decision:** `AddTask` applies the intake rules, and the domain records only what every task must satisfy:
- the id is the one given with `--id`, refused when it already exists, or else a slug of the title (`TaskId.sluggedFrom`: lower-case ASCII letters and digits, every other run of characters one hyphen, cut at a word break after 40 characters), numbered `-2`, `-3` and onwards when taken; tasks are never deleted, so an id once used stays taken;
- a ship or a scout must name at least one project, and every project it names must be in the project registry (ADR-041); a captain item may name none;
- every task it waits for (`--blocked-by`) must exist;
- the work key (`--key`) and the base branch (`--base`) are checked for their form only; matching a key to its project's tracker comes with the trackers of slice S6.
`Intake` itself demands only a title and no repeated project or blocker, so a migrated ship without a project is a valid task until the captain sets one.

**Motivation:** The primary can add a task in one line, a readable id comes for free, and the rules that depend on the registry live where the registry is read, not in the domain.

**Alternatives considered:**
- Ids always generated, such as a counter - unreadable in reports and branch names.
- The project rule in `Intake` - would make the migrated backlog unrepresentable.

**Consequences:**
- Good: the backlog keeps every task the old harness had, and new work is still checked at intake.
- Bad: a task's projects and blockers cannot be changed after intake yet.

**Revisit when:** The primary needs to change a task's projects, blockers or key after intake.

**Sources:** EDI rewrite specification, sections 8.1, 8.4, 9.2, 11.1 and 18; `domain.task.TaskId`, `Intake`, `application.task.AddTask`.
