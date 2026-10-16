# ADR-081: The pipeline adapter reads a branch's run from `axi status` and its spend from the pipeline's state, read-only

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The validation pipeline (no-mistakes) stays an external tool with `axi run`, `axi respond` and `axi status` (assumption 4); the worker drives it, and EDI only observes.
Section 12.1 polls a task's run step and outcome every 60 seconds while a run is active, and section 12.2 counts a running pipeline as a reason for an idle worker not to be stale.
U-06 records a task's pipeline spend at cleanup from the pipeline's per-round token deltas, read only, failed and cancelled rounds included, with an unknown spend kept unknown.
`no-mistakes axi status`, run in a worktree, prints a `run:` block for the current branch's active or most recent run in TOON.

**Decision:** `adapter.pipeline.NoMistakesPipeline` implements the `Pipeline` port with two operations.
`latestRunOf(worktree, branch)` runs `no-mistakes axi status` in the isolated copy, and `AxiStatus` reads the `run:` block: its id, branch, status, `head_sha`, `pr` and the first row of `active_steps`; a block for another branch, or none, is no run.
`tokensSpentOn(clone, branch)` opens `<pipeline home>/state.sqlite` read-only and sums `delta_input_tokens`, `delta_cache_read_tokens`, `cache_creation_tokens` and `delta_output_tokens` over every agent invocation of every run on that branch of the repository whose working path is the project clone; a missing database, an unreadable schema or any invocation without counts gives an unknown spend.
`PipelineRun.isRunning` treats every status but `completed`, `failed`, `cancelled`, `canceled` and `aborted` as running, so a status the pipeline adds later keeps a worker healthy rather than stale.
The executable and the pipeline home are `edi.pipeline.no-mistakes` and `edi.pipeline.home`, by default `no-mistakes` and `~/.no-mistakes`.

**Motivation:** Both reads use what the pipeline already exposes, without asking it to change, and neither can alter a run.

**Alternatives considered:**
- `axi status --run <id>` - EDI would first have to learn the run id, which only the worker sees when it starts the run.
- Parsing the pipeline's logs for spend - the database already holds per-invocation counts.

**Consequences:**
- Good: the fixtures in `src/test/resources/no-mistakes/` are captured from version 1.84.0, so a format change shows up as a failing test.
- Bad: the spend read depends on the pipeline's internal tables; a schema change makes the spend unknown, never wrong.

**Revisit when:** The pipeline reports spend through `axi`, or its status output changes.

**Sources:** EDI rewrite specification, sections 3.3, 12.1, 12.2 and U-06; ADR-007; `application.port.Pipeline`, `adapter.pipeline.NoMistakesPipeline`, `AxiStatus`, `PipelineSpend`, `domain.delivery.PipelineRun`; `docs/verification/no-mistakes.md`.
