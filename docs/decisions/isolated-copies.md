# Each task works in its own EDI-owned worktree, made from a fresh origin base, with git hooks installed through worktree configuration

**Kind:** Architecture

## Context

The old harness drew workers' copies from a shared pool of numbered slots, and relaunches more than once landed a worker in another task's copy.
Workers must never touch the captain's checkouts or the project clone's primary directory (I-07), and must branch from the freshly fetched origin base (I-13).
Every commit must refuse agent trailers and a subject without the task's key (I-09, I-10), and every push must pass the publish guard (I-38).
The old harness installed its hooks path through `GIT_CONFIG_*` variables, which drop inherited configuration such as a repository's scoped credential helper.

## Decision

Each task gets the worktree `$EDI_HOME/worktrees/<project>/<task-id>`, derived from the task by `EdiHome.worktreeOf` and never stored.
`IsolatedCopies.prepare` reuses that copy when it exists, checking it is a copy of the project's clone on the task's branch, or creates it: the parent must resolve inside `$EDI_HOME/worktrees/`, it fetches `origin`, and adds the worktree on a new branch from `origin/<base>`, the task's base or else the project's.
`IsolatedCopies.requireIsolated` holds before every launch, on real paths: the copy lies inside the worktrees, is not the clone's directory, is a linked worktree, and shares the clone's git directory.
No pool is kept, and a copy is removed only by cleanup after the landed-work test or on the captain's word ([cleanup](cleanup.md), [captains-word](captains-word.md)).
`WorktreeHooks` writes a `commit-msg` and a `pre-push` hook from `edi-core`'s `git-hooks/` templates into the worktree's own git directory, sets `extensions.worktreeConfig` on the clone and `core.hooksPath` with `git config --worktree`, at every launch.
The `commit-msg` hook is POSIX shell with the key filled in, refusing a subject without the key, any `Co-authored-by` line, a `Signed-off-by` naming a model, agent or tool, and a "Generated with" line.
The `pre-push` hook runs `edi publish-check` with git's push URL on the commits each pushed ref adds to both its remote branch and the base, `origin/<base>..<local head>` excluding the remote branch's head, and refuses the push when `edi` is missing or the check fails ([publish-guard](publish-guard.md)).
A task works on one project; the project's clone gets a credential helper bound to its forge account ([forge-adapters](forge-adapters.md)).

## Why

An address derived from the task cannot be reassigned, so the slot incidents cannot recur, and every path check uses what the filesystem and git report, so a symbolic link cannot pass for an isolated copy.
The hooks live and die with the worktree, nothing in the working tree carries them, every other setting of the clone still applies, and a shell hook keeps working when the core is down.

## Alternatives rejected

- A pool of pre-created copies - faster first start, at the cost of the reassignment failure class.
- Checking path text only - a symbolic link would lead a worker outside the home.
- `GIT_CONFIG_*` variables, a hook that calls the core, or a hooks directory in the working tree - drops inherited configuration, blocks commits while the core is down, or shows as untracked files.

## Consequences

- Good: a task's copy is findable from its id alone, and AT-07a, AT-09 and AT-10 run real commits and refusals in real worktrees.
- Bad: the first build in a new worktree starts cold, and a task across several projects cannot be dispatched yet.
- Bad: an environment exporting `GIT_CONFIG_*` with `core.hooksPath` would override the hooks; EDI's own launches unset them ([worker-sessions](worker-sessions.md)).

## When to revisit

When a task needs copies of several projects at once, worktree creation becomes slow, or a project needs git hooks of its own beside EDI's.

## How we got here

- 15/10/2026: the specification replaced the slot pool with task-addressed worktrees.
- 16/10/2026: slice S3 built the copies, their checks and the hooks through worktree configuration.
- 27/10/2026: the `pre-push` hook judges only the commits new to both the remote branch and the base, so neither a merged base nor a tightened rule strands a push (EDI-78).
