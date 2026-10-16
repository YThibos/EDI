# ADR-052: EDI makes each isolated copy from a fresh origin base and checks it before every launch

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-015 gives each task the worktree `$EDI_HOME/worktrees/<project>/<task-id>`, created with a fetch and `git worktree add -b <branch> <path> origin/<base>`.
A worker launch must refuse a worktree outside `$EDI_HOME/worktrees/` and the project clone's primary directory (I-07, AT-07a), and work must start from the freshly fetched origin base (I-13).
A task may carry its own base branch (U-04).
A project's clone is the registry's `clone`, by default `$EDI_HOME/projects/<name>`.

**Decision:** `IsolatedCopies.prepare` refuses a project without a clone and a task that works on more than one project, then either reuses the task's worktree when the directory exists, checking that it is an isolated copy of that clone with the task's branch checked out, or creates it: it checks first that the parent directory resolves inside `$EDI_HOME/worktrees/`, fetches `origin`, and adds the worktree on a new branch from `origin/<base>`, or on the existing branch when an earlier copy was removed.
The base is the task's own, else the project's `base` in the registry; a project without one is refused.
`IsolatedCopies.requireIsolated` holds before every launch, on real paths with symbolic links resolved: the copy lies inside `$EDI_HOME/worktrees/`, it is not the clone's directory, git reports it as a linked worktree rather than a primary checkout, and its common git directory is the clone's.
Nothing removes a copy in this slice; cleanup after the landed-work test arrives with slice S5.

**Motivation:** Every path check uses what the filesystem and git report, so a symbolic link or a clone placed among the worktrees cannot pass for an isolated copy.

**Alternatives considered:**
- Checking the path text only - a symbolic link would lead a worker outside the home.
- Fetching on every launch, also when the copy exists - a relaunch would take network time to change nothing in the copy.

**Consequences:**
- Good: AT-07a refuses both shapes before any process starts, and the refused copy is never created.
- Bad: a task across several projects cannot be dispatched yet.

**Revisit when:** A task needs isolated copies of several projects at once, or worktree creation becomes slow.

**Sources:** EDI rewrite specification, sections 8.4, 11.3 and I-07, I-13, U-04 (decision E5); ADR-015; `application.dispatch.IsolatedCopies`, `adapter.git.Git`.
