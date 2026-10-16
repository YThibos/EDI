# ADR-091: Worker health is read from hooks, the tmux server, the boot and the pipeline, and each condition is noticed once

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-070 set the health rules of section 12.2 and left one clause for slice S5: an idle worker with no declared wait, no running pipeline and no pending inbox message raises one "waiting too long" wake after `supervision.staleAfter`.
A worker that drives the validation pipeline idles while the pipeline reviews, tests and watches CI, often for longer than twenty minutes.
Section 12.1 reads a task's run every 60 seconds while it is active.

**Decision:** Everything ADR-070 decided stands, with one change: `SupervisionRules.noticeDue` takes what an idle worker awaits, an `Awaited` of a message it has not received and of its task's pipeline run, and a worker awaiting either is never stale.
`DeliveryRound` asks `ObservePipelines` every `edi.delivery.pipelines` (PT60S by default) to read the latest run of every ship delivered `draft-review` whose worker has not yet handed its work over; `PipelineRunStore` keeps the latest reading per task, recording `PipelineRunSeen` for a new run and `PipelineRunFinished` when a run leaves its running states.
`JudgeWorkers` counts the pipeline as running from that stored reading.

**Motivation:** A worker waiting on its own pipeline is doing its job, so the primary is not woken for it, and the reading comes from the pipeline itself rather than from the worker's word.

**Alternatives considered:**
- Requiring the worker to declare every pipeline wait with `paused --until` - the brief still asks for it, but a forgotten declaration would raise a false alarm.
- Reading the pipeline inside every judgement - one slow pipeline call would slow every supervision round.

**Consequences:**
- Good: `edi task show` shows the run and its step, and pipeline progress never wakes the primary.
- Bad: a run the pipeline reports as running forever keeps its worker from ever being stale; the busy ceiling does not apply to an idle worker.

**Revisit when:** Claude Code reports a pending permission prompt as a state of its own, or the pipeline reports progress through events.

**Sources:** EDI rewrite specification, sections 12.1 and 12.2 and I-26; ADR-070, ADR-081; `domain.worker.SupervisionRules`, `Awaited`, `application.supervision.JudgeWorkers`, `application.delivery.ObservePipelines`, `DeliveryRound`, `adapter.store.PipelineRunStore`.
