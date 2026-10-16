# ADR-059: A mechanical task names its exact change, and its done is checked before it counts

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The `mechanical` category runs on the cheapest model, made foolproof three ways (section 8.6, decision E21): the primary picks it only when the brief states the exact expected change; a worker's done is accepted only if the full verify profile passes and the diff touches only the files the brief named, otherwise EDI re-dispatches the task as `implementation` on its own and records why; and a mechanical task never edits a decision, a migration, security configuration or a test's expected values.

**Decision:** `edi dispatch --category mechanical` needs the change in `--spec` and every file it touches in `--files` (`ExpectedChange`), and the project's `verify` command in the registry.
It refuses files that `ProtectedFile` recognises: decision records (a `decisions`, `adr` or `adrs` directory, or an `ADR-` file), migrations (a `migration` or `migrations` directory, or a Flyway `V<n>__` script), security configuration (any path naming security), and tests and their expected values (a `test`, `tests`, `__tests__` or `__snapshots__` directory, `*Test.java`, `*Tests.java`, `*IT.java`, `*.spec.*`, `*.test.*`).
A mechanical worker's `edi status done` sets its done check to `checking`; every other done is accepted at once.
The supervision round (ADR-061) runs `CheckMechanicalChange` off the scheduler thread: the files changed since `origin/<base>`, committed or not, must all be named and must include at least one named file, and `sh -c <verify>` must succeed in the worktree within 60 minutes, with its output kept in `data/tasks/<task-id>/mechanical-verify.log`.
An accepted check records `MechanicalChangeAccepted`; a refused one records `MechanicalChangeRefused` with the reason, closes the worker's window and launches an `implementation` worker in the same copy, whose brief says why the mechanical done was refused.

**Motivation:** The cheap model gets only work a reviewer can verify at a glance, and nothing it touches beyond that is ever accepted silently.
Treating every test file as protected keeps a test from being bent to pass; a rename that reaches tests is implementation work.

**Alternatives considered:**
- Running the verify command inside `edi status done` - the worker's call would wait for a whole build.
- Asking the worker for proof that verify passed - the check would trust the cheap model's own claim.

**Consequences:**
- Good: the guard needs no new state beyond the worker's done check, and a crash in mid-check leaves the check to run again.
- Bad: a project without a `verify` command cannot use the mechanical category, and the check costs one full build.

**Revisit when:** Slice S5 checks every ship's done against its pipeline and forge, or the protected patterns refuse a change a reviewer would accept.

**Sources:** EDI rewrite specification, section 8.6 (decision E21); ADR-025; `domain.mechanical.ExpectedChange`, `ProtectedFile`, `domain.worker.DoneCheck`, `application.worker.CheckMechanicalChange`, `adapter.build.ProjectBuild`.
