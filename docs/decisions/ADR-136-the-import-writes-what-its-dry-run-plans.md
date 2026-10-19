# ADR-136: The import writes what its dry run plans, beside EDI's own work, and overwrites a home in use only when told

**Date:** 19/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 18 lists what `edi migrate firstofficer --from <home>` imports at the cutover, and section 20.3 runs it once for real; until slice S10 only `--dry-run` existed (ADR-044).
The captain asked for an import that is idempotent and safe to run again, that refuses to overwrite an EDI home that is not empty unless explicitly told, and that never modifies the old home.
The captain's EDI home at the cutover is not empty: the shadow run of slice S9 leaves configuration, placeholder memory and EDI's own tasks and decisions in it.
The command reaches the core over HTTP, and the old home holds about half a gigabyte of task folders.

**Decision:** `ForkMigrationPlanner` makes one `MigrationPlan` of everything the import writes: the tasks with their holds and decisions, the two sequences to continue, the home's files with their exact content (`HomeImport`), the archive to copy and the folder links; `MigrateFirstofficer` presents it as a dry run or applies it, and both report the same `BacklogSurvey` and `HomeSurvey` of what the plan meets in this EDI home.
- The store import (`BacklogImport`) only adds: a task whose id EDI already holds is left as it is and counted as already in EDI, and a task whose decision number EDI already uses for another task is not imported and is listed; tasks are inserted first and their blockers added once every imported task exists, each with one `TaskImported` event, which wakes nobody and feeds no follower.
- `decision_seq` continues after the larger of EDI's own number and the old home's highest (F6 of the session prep), and the `EDI` key sequence after the larger of its own and the highest key the old home uses.
- A file the plan writes is added when absent and left alone when its bytes are the same; one that differs is replaced only with `--overwrite`, and the replaced file is kept beside it as `<file>.before-migration` (`HomeWriter`).
- The EDI home counts as in use when its store holds a task or a decision the plan does not account for, or when any file, archive copy or folder link of the plan differs from what the home holds; a real run then refuses with one sentence naming what it found, and `--overwrite` imports beside it.
- A run after a complete import finds every task already in EDI and every file unchanged, so it changes nothing and records no event.
- The old home is only read: files, `git remote get-url` on its clones, and the one environment variable of ADR-139.

**Motivation:** One plan for both runs means the real run cannot do what the dry run did not show, and adding beside EDI's own work lets the captain import into the home the shadow run proved without losing either side.

**Alternatives considered:**
- Refusing any home with content - the cutover home always has content, so `--overwrite` would be needed every time and protect nothing.
- Replacing EDI's tasks with the old home's on `--overwrite` - destroys work EDI has done since, which no flag should do silently.
- A separate import service beside the dry-run planner - two readings of the old home could disagree, which the captain's "the real run must produce exactly what the dry run plans" rules out.
- Re-serialising every configuration file on each run - would make a second run differ from the first.

**Consequences:**
- Good: the dry run on the captain's home shows every file it would add or replace before anything changes.
- Good: an interrupted import is finished by running it again.
- Bad: a re-import after the old home changed needs `--overwrite` to refresh files that differ.

**Revisit when:** The old home is archived (section 20.4) and the import is no longer needed.

**Sources:** EDI rewrite specification, sections 18 and 20.3; session prep report, B1 and F6; ADR-044, ADR-137 to ADR-141; `application.migration.ForkMigrationPlanner`, `MigrateFirstofficer`, `BacklogImport`, `HomeWriter`, `MigrationReport`, `web.migration.MigrationController`, `MigrationReportText`, `edi-cli` `MigrateFirstofficerCommand`; AT-41, AT-47.
