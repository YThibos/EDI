# ADR-141: Adopting an open task brings its branch into EDI's own clone, and its first dispatch resumes the carried session

**Date:** 19/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 20.3 adopts a task still in flight on the old harness with `edi task adopt <id> --branch <branch> --worktree <path> [--session <uuid>]`: it imports the task, registers the existing copy, moved under `$EDI_HOME/worktrees/` only when no process holds it, and records the Claude session id so the next launch can resume; a task is resolved by its branch, never by a pool slot.
The old harness's copies are worktrees of its own clones, inside the old home that is moved to its archive a week later (section 20.4), and EDI launches a worker only in a copy of its own clone under its worktrees (I-07, ADR-052, ADR-015).
Claude Code keeps a session's transcript in a folder named after the working directory and resumes a session from the folder of the directory it starts in.
The adoption needs the old home to import the task, and the session prep found the runbook's commands missing `--from` (F1).

**Decision:** `edi task adopt <id> --from <home> --branch <branch> --worktree <path> [--session <uuid>]` runs `AdoptForkTask`:
- the task is taken from the same plan as the import (ADR-136, ADR-137), so it lands exactly as `edi migrate firstofficer` would write it; done work and the old harness's own work are refused, and an unkeyed task takes the branch's key while a task keyed otherwise is refused;
- the old copy must have the branch checked out, no process working in it and no uncommitted change, so nothing unlanded is left behind and nothing is still writing there;
- the branch is fetched by ref into the project's clone in the EDI home, and `IsolatedCopies` makes the task's copy on it with its hooks; when EDI's copy already exists it must hold the old copy's head;
- with `--session`, the session's transcript is found in Claude Code's session folders and copied into the folder of EDI's copy (`ClaudeSessions`); a session without a transcript is refused;
- an `adoption` row records the branch and the session, with a `TaskAdopted` event; adopting again with the same branch and session records nothing new;
- the task's first dispatch uses the adopted branch, and `LaunchWorker` resumes the adopted session there with the EDI brief as its first prompt, still recorded as the task's dispatch, so the activity ledger and the timeline read as for any first dispatch;
- the old copy is left as it was, for the rollback of section 20.5 and the slot return of section 20.4.
`edi migrate firstofficer` and `edi task adopt` both take `--from`, in their help and in the README.

**Motivation:** A copy of EDI's own clone survives the old home's archive and passes every isolation check a launch makes, and taking the branch by ref carries every commit without touching the old copy.

**Alternatives considered:**
- Moving the old worktree under EDI's worktrees - it would stay a worktree of the old clone, break when the old home moves, and fail EDI's isolation check.
- Re-linking the old worktree to EDI's clone by hand - rewrites git's administrative files, which no git command supports.
- Carrying uncommitted changes as a patch - work the old worker left unfinished is safer committed by the captain's decision than replayed by EDI.
- Resuming the session in the old copy's directory - a worker outside EDI's worktrees is what I-07 forbids.

**Consequences:**
- Good: an adopted task continues in the same Claude session on the same branch, under EDI's hooks and supervision.
- Bad: EDI copies a transcript into Claude Code's session folders, a layout Claude Code does not document; if it changes, adoption without `--session` still works.

**Revisit when:** Claude Code resumes a session from another directory, or the old home is archived.

**Sources:** EDI rewrite specification, sections 11.3, 11.9, 20.3, 20.4, 20.5 and I-07, I-25; session prep report, B1 and F1; ADR-015, ADR-052, ADR-073, ADR-136, ADR-137; `application.migration.AdoptForkTask`, `AdoptedTask`, `application.dispatch.LaunchWorker`, `DispatchTask`, `IsolatedCopies`, `adapter.runtime.ClaudeSessions`, `adapter.store.AdoptionStore`, `domain.task.Adoption`, `web.task.TaskController`, `AdoptRequest`, `edi-cli` `TaskAdoptCommand`; AT-48.
