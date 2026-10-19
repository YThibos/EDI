# ADR-140: The old home's data is archived read-only, and each open task links its old folder

**Date:** 19/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 18 copies the old `data/done-archive.md` and `data/note-archive.md`, the task folders `data/<id>/` and the historical files and held work into `data/archive/firstofficer/`, read-only and not imported as rows, and links the folders of tasks still open from their new `data/tasks/<id>/`.
The old home is archived itself after a week (section 20.4), so EDI must not depend on its paths.
EDI writes a task's brief and report into `data/tasks/<id>/`.
The old home's data is about half a gigabyte in twenty thousand files, and the import runs inside one call to the core.

**Decision:** `ForkArchive` lists every file and symbolic link the archive keeps: each file directly under the old `data/` as itself, each folder under `data/` below `tasks/`, the old `config/` below `config/` and `state/held/` below `state/held/`; the rest of `state/` is the old harness's runtime and is not kept.
`HomeWriter` copies each one into `data/archive/firstofficer/` with its time of last change, keeps a link as a link, and makes every copied file read-only and readable by its owner alone, as the old home holds the captain's private material; a copy with the same size and time of last change is unchanged, so a second run reads no file twice.
The import also keeps the old memory and registry files here as they were, so nothing the memory split or the registry import leaves behind is lost.
For each imported task that is not done and has an old folder, the import makes `data/tasks/<id>/firstofficer`, a relative link to its archived folder, so the task's own folder stays EDI's.
Archive copies that differ are replaced on `--overwrite` without keeping the earlier copy, since the old home still holds its source.

**Motivation:** A copy survives the old home's move to its archive, a relative link survives a move of the EDI home, and comparing sizes and times keeps a run within the command's patience.

**Alternatives considered:**
- Hard links instead of copies - making the archive read-only would change the old home's own files, which the import must never touch.
- Linking `data/tasks/<id>/` itself to the archived folder - EDI would write its brief into the read-only archive.
- Comparing every archived byte - a second run would read the whole old home again.

**Consequences:**
- Good: the old reports, boards and screenshots stay one link away from the task they belong to.
- Bad: the archive doubles the old data on disk until the captain prunes it.

**Revisit when:** The archive is no longer consulted.

**Sources:** EDI rewrite specification, sections 7.1, 8.7, 18 and 20.4; `adapter.fork.ForkArchive`, `ArchivedFile`, `ForkHome.archiveOf`, `application.migration.ArchivePlan`, `HomeWriter`, `FolderLink`, `HomeImportPlanner`; AT-47.
