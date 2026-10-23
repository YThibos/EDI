# Moving from the old harness

`edi migrate firstofficer` imports the old harness's home into EDI, its backlog, holds and decisions, its memory and configuration and an archive of its data, exactly as its dry run plans ([old-harness-migration](decisions/old-harness-migration.md)).
`edi task adopt` takes over a task still open there, with its copy, its branch and its Claude Code session ([old-harness-migration](decisions/old-harness-migration.md)).

```sh
edi migrate firstofficer --from /path/to/old/home --dry-run    # the plan, and what it would add or replace in this EDI home
edi migrate firstofficer --from /path/to/old/home              # the import; running it again changes nothing
edi migrate firstofficer --from /path/to/old/home --overwrite  # into a home that already holds work or files of its own
edi task adopt fit-the-lamp --branch feature/EDI-14 \
    --worktree /path/to/old/copy --session <uuid>              # an open task, on its branch and in its session
```

## The import

The import only reads the old home.
It adds the old tasks, holds and decisions beside EDI's own, continues the decision and `EDI` key sequences after the highest either side uses, and sets aside the old harness's own work ([old-harness-migration](decisions/old-harness-migration.md)).
It writes the project registry, the dispatch rules, the machine profile's missing settings, the personal configuration, the publish deny-list and the memory files, adding only what the EDI home lacks and leaving the old harness's own mechanics out of the memory ([old-harness-migration](decisions/old-harness-migration.md)).
It copies the old data read-only into `$EDI_HOME/data/archive/firstofficer/`, linking each open task's old folder from `data/tasks/<task>/firstofficer` ([old-harness-migration](decisions/old-harness-migration.md)).
A file that differs from the EDI home's is replaced only with `--overwrite`, and the replaced one is kept as `<file>.before-migration`.
The dry run lists every such file, every section of the old memory with the file it goes to, and every gap left for the captain, such as a project's forge account or tracker.

## Adoption

Adoption reads the task from the old home whose clone the copy belongs to, or from the home `--from` names, and needs the old copy on the task's branch.
While a process still works in the old copy, adoption registers it in place and EDI launches no worker in it.
Once nothing works there, adopting again, or adopting the first time, moves the copy with its uncommitted work under `$EDI_HOME/worktrees/` as a copy of the project's clone in the EDI home.
It carries the session's transcript beside the copy, so the task's first dispatch resumes the session there.
