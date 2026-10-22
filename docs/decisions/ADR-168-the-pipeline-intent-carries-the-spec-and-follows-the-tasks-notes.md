# ADR-168: The pipeline intent carries the brief's spec and is rewritten whenever the task is noted

**Date:** 22/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-082 composed the pipeline intent from the task's notes and the craft rules only, and wrote it once, at dispatch.
The validation pipeline's reviewer judges a change against that intent alone, so it took requirements the brief's spec states, such as a dimmer the spec asks for or a test it names, for scope creep, and a worker and the primary had to settle a review gate that should never have opened (EDI-27, EDI-23, EDI-40, JUSTMD-57).
A scope change the primary records with `edi task note` after dispatch, and steers to the worker, reached neither the written brief nor the intent, so the reviewer flagged that work as well.
The pipeline still takes its intent only as the value of `--intent`, and keeps the intent a run started with for the whole run.

**Decision:** `PipelineIntent.of` composes the intent from three parts: a `## Captain's intent` section with the task's notes, a `## Spec` section with the brief's Spec section (the plan of a planned ship followed by the dispatch's `--spec`), and the craft rules block, verbatim, when the project is flagged.
`WrittenBriefs` writes `brief.md`, and `intent.md` for a ship delivered `draft-review`, both at launch and whenever the task is noted: `NoteTask` calls `RewriteBrief`, which rebuilds the brief facts for the task's latest worker through `GatherBriefFacts`, the same gatherer `LaunchWorker` uses, and rewrites both files while the task is in flight and EDI has written its brief.
A steer that changes scope reaches the pipeline through the note the primary records for it.
The brief's Validation and delivery section says the intent comes from the Captain's intent and Spec sections and is rewritten when the task is noted.

**Motivation:** The reviewer judges the change against what the worker was actually asked for, so a requirement the spec or a later scope note states is never reported as scope creep.
Rewriting at the note keeps the brief, the intent and the task's notes saying the same thing without the worker assembling anything by hand.

**Alternatives considered:**
- Passing every steer message into the intent - most steers answer a gate or a merge conflict and say nothing about scope, and the reviewer would read them as requirements.
- An `edi intent` command the worker calls at run time - a new command and endpoint for what one file, kept current, already does, and AT-34 would no longer read the very text the worker passes.
- Rewriting from an event follower - the follower runs asynchronously, so a worker acting on the steer could start the pipeline before the intent changes.

**Consequences:**
- Good: the reviewer sees the spec's acceptance criteria and every scope note recorded before the run starts.
- Good: `edi brief <task>` prints the brief as it stands after the latest note.
- Bad: a note recorded after the pipeline run has started does not reach that run; the primary settles its gate as before.
- Bad: the intent travels as one command-line argument, now with the spec, which stays within limits for a task's spec but would not for fleet-sized data (I-36).

**Revisit when:** The pipeline reads its intent from a file or standard input, or lets a running run take a new intent.

**Sources:** EDI-30; ADR-082, which this supersedes; ADR-055; `domain.delivery.PipelineIntent`, `application.brief.WorkerBriefs.pipelineIntentFor`, `application.brief.WrittenBriefs`, `application.dispatch.RewriteBrief`, `application.dispatch.GatherBriefFacts`, `application.task.NoteTask`, `BriefSections.draftReview`, `application.dispatch.LaunchWorker`.
