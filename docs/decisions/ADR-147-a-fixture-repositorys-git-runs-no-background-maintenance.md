# ADR-147: A fixture repository's git runs no background maintenance

**Date:** 19/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Since git 2.47, `git commit`, `git fetch`, `git merge` and the other commands that run automatic maintenance start `git maintenance run --auto --detach`.
That process takes `objects/maintenance.lock`, detaches into the background, checks its tasks and only then removes the lock, so the lock outlives the command that started it.
The CI runners have git 2.55; on main after slice S10, AT-47's snapshot of the old home caught `projects/harbour/.git/objects/maintenance.lock` from the fixture's last commit, and the snapshot after the import no longer held it.
ADR-065 waits for every program a helper such as `FixtureRepository` runs, so a test can rely on that program having finished when the helper returns.
ADR-053 has `FixtureRepository` clear the `GIT_CONFIG_*` variables, because configuration injected through them outranks a repository's own.

**Decision:** `FixtureRepository` runs every git command with `-c maintenance.auto=false`, so no command it runs leaves a process behind.

**Motivation:** A fixture repository changes only when a test changes it, on every git version, which a comparison of its files before and after needs.
A command-line setting reaches every command the helper runs, including those on a repository it only wraps with `FixtureRepository.at`, and leaves the repository's own configuration as the test made it.

**Alternatives considered:**
- Waiting for the lock to go - the background process belongs to no test, and waiting on it means polling with a sleep.
- `maintenance.autoDetach=false` - the maintenance would then run in the foreground after every fixture commit, for work no test needs.
- Writing `maintenance.auto` into each fixture repository's own configuration - it would also quiet EDI's own git there, but misses every repository the helper only wraps with `FixtureRepository.at`.
- Setting it through `GIT_CONFIG_COUNT` - ADR-053 clears those variables so that no injected configuration reaches a fixture, and adding some back blurs that rule.

**Consequences:**
- Good: a test that compares a fixture repository before and after sees only what the code under test changed.
- Bad: git commands that EDI itself runs in a fixture repository during a test, such as a fetch or a push, can still start background maintenance there; no test compares those repositories' files.

**Revisit when:** git stops running maintenance in the background after a command, or a test compares the files of a repository EDI's own git has written to.

**Sources:** Main's CI run after slice S10, the SonarQube job's AT-47 failure; git 2.55 `builtin/gc.c` `maintenance_run_tasks` and `run-command.c` `prepare_auto_maintenance`; ADR-053, ADR-065; `edi-core` `testing.FixtureRepository`; AT-47.
