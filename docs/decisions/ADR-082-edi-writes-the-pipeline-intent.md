# ADR-082: EDI writes the pipeline intent beside the brief, with the craft rules verbatim

**Date:** 16/10/2026

**Status:** Superseded by ADR-168

**Kind:** Design

**Context and constraints:** For a project flagged `craftRules`, the captain's craftsmanship rules ride in the pipeline intent together with the task's full acceptance criteria and its offline-verification stance (I-34, section 13.5); AT-34 checks the generated intent for the rules block verbatim and the acceptance criteria.
The brief names the exact pipeline invocation (section 10.4, item 8), and a task's notes hold the captain's intent verbatim, with his acceptance criteria and offline stance in his own words (section 11.1).
The pipeline takes the intent only as the value of `--intent`.

**Decision:** At every dispatch of a ship delivered `draft-review`, `LaunchWorker` writes `intent.md` in the task directory beside `brief.md`.
`PipelineIntent.of` composes it from the brief's Captain's intent section, the task's notes, followed by a blank line and the craft rules block from `memory/craft-rules.md` with only its surrounding whitespace trimmed, when the project is flagged.
The brief's Validation and delivery section gives the one command to run from the isolated copy, `no-mistakes axi run --base-branch <base> --intent "$(cat '<task directory>/intent.md')"`, and tells the worker to pass it unchanged.

**Motivation:** The worker no longer assembles the intent by hand, so the rules block cannot be paraphrased, shortened or forgotten on the way to the pipeline.

**Alternatives considered:**
- Separate fields for acceptance criteria and the offline stance - the captain gives them in his own words, and splitting them would paraphrase him.
- Passing the intent on standard input - the pipeline does not read it from there.

**Consequences:**
- Good: AT-34 reads the very file the worker passes.
- Bad: the intent travels as one command-line argument, which stays within limits for a task's intent but would not for fleet-sized data (I-36).

**Revisit when:** The pipeline reads its intent from a file or standard input.

**Sources:** EDI rewrite specification, sections 10.4, 11.1, 13.5 and I-34 (AT-34); ADR-055; `domain.delivery.PipelineIntent`, `application.brief.WorkerBriefs.pipelineIntentFor`, `BriefSections`, `BriefFacts.intentPath`, `application.dispatch.LaunchWorker`.
