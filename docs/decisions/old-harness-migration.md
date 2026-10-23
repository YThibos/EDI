# The old harness's home is imported from one plan its dry run shows, only ever read, and open tasks are adopted with their copies

**Kind:** Design

## Context

The cutover imports the old harness's backlog, decisions, memory, configuration and data into EDI, and adopts tasks still in flight on it.
The old backlog's Markdown grammar is defined by its backlog tool, tasks-axi, and counts that match tasks-axi's own reading are the only convincing proof.
The import must be idempotent, refuse to overwrite an EDI home in use unless told, and never modify the old home, which is archived a week later.
The cutover home already holds a shadow run's validated configuration and EDI's own tasks, the old memory names the captain's own headings and employer projects, and the old registry is prose with no forge account or tracker.

## Decision

`ForkMigrationPlanner` makes one `MigrationPlan` of everything the import writes, and `MigrateFirstofficer` presents it as a dry run or applies it, so the real run cannot do what the dry run did not show.
`adapter.fork` reads the backlog exactly as tasks-axi does (`BacklogLines`, `TailTags`, `HoldReason`), and every entry is mapped through EDI's domain constructors; an entry the domain refuses becomes a note listed for the captain.
The old harness's own work is set aside, waits on something outside EDI are listed rather than carried, and the decision and `EDI` key sequences continue above the highest number either home or any line of the old one uses.
The store import only adds: a task EDI already holds is left as it is, and a file the plan writes is added when absent, left when equal, and replaced only with `--overwrite`, keeping the earlier one as `<file>.before-migration` (`HomeWriter`).
`MemoryImport` routes each section of the old memory by generic rules on its heading, into the profile, the machine memory or the archive, and makes the specification's named edits on the way (`CaptainMemoryEdits`, `LearningsFilter`).
The configuration becomes EDI's by adding only what EDI lacks, and every missing account, base or tracker is listed with the line to add, never guessed (`RegistryImport`, `DispatchRulesFile.withWhatItLacks`, `DenyListImport`).
`ForkArchive` copies the old data read-only into `data/archive/firstofficer/`, and each open task links its old folder from `data/tasks/<id>/firstofficer`.
`edi task adopt` imports a task from the same plan, registers its old copy in place while a process works there, and once none does moves its files into a fresh copy of EDI's clone with every uncommitted change; with `--session` the transcript is copied so the first launch resumes it (`AdoptForkTask`, `ClaudeSessions`).
AT-41 and AT-47 compare every entry of the old home but its access times before and after, naming each one that changed.

## Why

One plan for both runs, and an import that only adds, let the captain import into the home the shadow run proved without losing either side.
Generic routing rules keep tracked code person-agnostic, and taking only what the old home states keeps the captain in charge of every account and tracker.
The copy the old worker used, with everything it left uncommitted, becomes the task's copy, and once under EDI's worktrees it passes every isolation check ([isolated-copies](isolated-copies.md)).

## Alternatives rejected

- Calling tasks-axi from the core - a Node tool in EDI's runtime and a run-time dependency on the old harness.
- Refusing any home with content, or replacing EDI's tasks on `--overwrite` - the cutover home always has content, and no flag should destroy work silently.
- The memory tables as fixed lists of the captain's headings - would put personal headings into tracked files.
- Guessing an account or tracker from the old prose - a wrong account sends forge calls under the wrong identity.
- `git worktree move` for adoption - the copy would stay a worktree of the old clone and fail EDI's isolation check.

## Consequences

- Good: the dry run on the captain's home matched tasks-axi's counts and showed every file it would add or replace, and an interrupted import is finished by running it again.
- Bad: EDI owns a port of a grammar it stops needing after the cutover, the archive doubles the old data on disk, and adoption relies on Claude Code's undocumented session folder layout.

## When to revisit

When the old home is archived and the import is no longer needed; the reader and the adoption can then be removed.

## How we got here

- 15/10/2026: slice S1 built the dry run of the backlog import.
- 19/10/2026: slice S10 completed the import of the home and adoption, after the dry run on the captain's home found a key named only in a body, seventeen tasks of the old harness's own project and an internal label in a captain-facing line; a fixture's background git maintenance led to comparing every entry of the old home.
