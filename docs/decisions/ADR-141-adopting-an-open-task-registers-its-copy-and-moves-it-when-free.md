# ADR-141: Adopting an open task registers its copy in place and moves it under EDI's worktrees once nothing works there

**Date:** 19/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 20.3 adopts a task still in flight on the old harness with `edi task adopt <id> --branch <branch> --worktree <path> [--session <uuid>]`: it imports the task, registers the existing copy, moved under `$EDI_HOME/worktrees/` only when no process holds it, and records the Claude session id so the next launch can resume; a task is resolved by its branch, never by a pool slot.
The old harness's copies are worktrees of its own clones, inside the old home that is moved to its archive a week later (section 20.4), and EDI launches a worker only in a copy of its own clone under its worktrees (I-07, ADR-052, ADR-015).
Claude Code keeps a session's transcript in a folder named after the working directory and resumes a session from the folder of the directory it starts in.
The adoption needs the old home to import the task, and the session prep found the runbook's commands missing `--from` (F1).

**Decision:** `edi task adopt <id> --branch <branch> --worktree <path> [--session <uuid>] [--from <home>]` runs `AdoptForkTask`:
- the old home is the one whose `projects/` holds the clone the old copy belongs to, unless `--from` names it;
- the task is taken from the same plan as the import (ADR-136, ADR-137), so it lands exactly as `edi migrate firstofficer` would write it; done work and the old harness's own work are refused, an unkeyed task takes the branch's key, and a task keyed otherwise, in the old home or in EDI, is refused;
- the old copy must be an isolated copy with the branch checked out;
- while a process works in the old copy, it is registered in place: EDI's place for the task's copy under its worktrees links to it, and nothing in it changes;
- once no process works there, the branch is fetched by ref into the project's clone in the EDI home, a copy of that clone is added on the branch without a checkout at EDI's place, the old copy's files move into it with every uncommitted change and untracked file, and the index is rebuilt from the branch, so the work stays as the old worker left it, unstaged; the old copy's directory is then gone, and adopting again finds the moved copy;
- `IsolatedCopies` installs the copy's hooks as for any copy;
- with `--session`, the session's transcript is found in Claude Code's session folders and copied into the folder of EDI's copy (`ClaudeSessions`); a session without a transcript is refused;
- an `adoption` row records the branch and the session, with a `TaskAdopted` event; a task the import already brought in unkeyed is keyed on the branch's key, with a `TaskKeyed` event; adopting again with the same branch and session records nothing new;
- the task's first dispatch uses the adopted branch, and `LaunchWorker` resumes the adopted session there with the EDI brief as its first prompt, still recorded as the task's dispatch, so the activity ledger and the timeline read as for any first dispatch.
`edi migrate firstofficer` and `edi task adopt` both take `--from`, in their help and in the README, and both wait up to thirty minutes for the core's answer, because the import copies the whole archive and the adoption fetches and moves a copy.

**Motivation:** The copy the old worker used, with everything it left uncommitted, becomes the task's copy, and once it is a copy of EDI's own clone under EDI's worktrees it passes every isolation check a launch makes and survives the old home's archive.

**Alternatives considered:**
- Moving the old worktree with `git worktree move` - it would stay a worktree of the old clone, break when the old home moves, and fail EDI's isolation check.
- Re-linking the old worktree to EDI's clone by rewriting its `.git` file - rewrites git's administrative files, which no git command supports.
- A fresh copy from EDI's clone, refusing a copy with uncommitted changes or a process in it - leaves the old worker's unfinished work behind, which section 20.3 keeps.
- Resuming the session in the old copy's directory - a worker outside EDI's worktrees is what I-07 forbids.

**Consequences:**
- Good: an adopted task continues in the same copy, with its uncommitted work, in the same Claude session on the same branch, under EDI's hooks and supervision.
- Bad: a copy registered in place is still a copy of the old clone outside EDI's worktrees, so EDI's isolation checks refuse to launch a worker in it; the captain adopts the task again once the old worker has stopped, and the copy moves.
- Bad: the move takes the old copy away from the old harness, so the rollback of section 20.5 re-dispatches that task from its branch rather than from the old copy, and the old clone keeps a stale worktree entry until `git worktree prune`.
- Bad: the files move by rename, so the old copy and the EDI home must share a file system.
- Bad: EDI copies a transcript into Claude Code's session folders, a layout Claude Code does not document; if it changes, adoption without `--session` still works.

**Revisit when:** Claude Code resumes a session from another directory, or the old home is archived.

**Sources:** EDI rewrite specification, sections 11.3, 11.9, 20.3, 20.4, 20.5 and I-07, I-25; session prep report, B1 and F1; ADR-015, ADR-052, ADR-073, ADR-136, ADR-137; `application.migration.AdoptForkTask`, `AdoptedTask`, `application.dispatch.LaunchWorker`, `DispatchTask`, `IsolatedCopies`, `adapter.fork.ForkHome`, `adapter.runtime.ClaudeSessions`, `adapter.store.AdoptionStore`, `domain.task.Adoption`, `web.task.TaskController`, `AdoptRequest`, `edi-cli` `TaskAdoptCommand`, `CoreExchange`; AT-48.
