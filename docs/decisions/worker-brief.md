# The worker brief and the pipeline intent are written from one template and the task's own facts, and rewritten when the task is noted

**Kind:** Design

## Context

The brief has fixed sections in order and stays within 120 lines (I-35), and a worker never addresses the captain (I-01).
Project rules come from the registry: craft rules, the AI scaffolding posture, the ship brief rules, no legacy below 1.0.0 and no SNAPSHOT.
For a project flagged `craftRules`, the captain's craftsmanship rules ride in the pipeline intent verbatim with the task's acceptance criteria (I-34), and the pipeline takes its intent only as the value of `--intent`.
The pipeline's reviewer judges a change against that intent alone, so a requirement stated only in the spec, or in a scope note recorded after dispatch, read as scope creep.
Until the time-booking tool's continuous capture is confirmed, a worker's report ends with a `worked:` line.

## Decision

`claude/worker/brief-template.md` holds every fixed sentence of the brief with named placeholders; `WorkerBriefs` fills them from `BriefFacts`, which `GatherBriefFacts` collects from the task, its worker, the registry entry, the dispatch, the base, the paths and the craft rules, and a line holding only a placeholder that fills in empty is dropped (`BriefTemplate`).
The task's notes are the captain's intent and the dispatch's `--spec` is the spec; `BriefSections` writes the variable sections: project rules, validation and delivery per kind and delivery mode, the definition of done, and a mechanical task's files and limits.
A project with `craftRules: true` and no `memory/craft-rules.md` is refused at launch, and the definition of done asks for the `worked:` line only while the machine profile's `time.requireWorkedLine` is true.
A ship's brief, when the project keeps a decision log, says to record each architecture or design decision in the project's log in its own form, and only those ([decision-log](decision-log.md)).
`PipelineIntent.of` composes `intent.md` from a `## Captain's intent` section with the notes, a `## Spec` section with the brief's Spec section, a `## Commits` section that names the work key the pipeline puts in front of its own commit subjects and title ([pipeline-commit-keys](pipeline-commit-keys.md)), and the craft rules block verbatim when the project is flagged; the brief gives the one command that passes it to the pipeline unchanged, with the steps the project's runs skip ([pipeline-steps](pipeline-steps.md)).
`WrittenBriefs` writes `brief.md`, and `intent.md` for a `draft-review` ship, at every launch and whenever the task is noted: `NoteTask` calls `RewriteBrief`, which rebuilds the facts for the task's latest worker and rewrites both while the task is in flight.
`DispatchTask` refuses a dispatch whose brief would run past 120 lines before it saves or defers it, naming the longest of the template's sections as each renders, so the primary shortens that section or points the worker to a file (`BriefLength`).
The count takes the captain's intent as one line and leaves out the plan a piloted ship carries in from its research task ([research-first](research-first.md)), since the primary can shorten neither.
A relaunch or a rewrite after a note is never refused.
`edi brief <task>` prints the brief as it stands, or with `--category` the brief a dispatch would write, refused as that dispatch would be.

## Why

The wording of the brief can be read and reviewed in one Markdown file, and the code decides only which parts apply.
The worker no longer assembles the intent by hand, so the rules block cannot be paraphrased or forgotten, and the reviewer judges the change against what the worker was actually asked for.
Turning the worked line off is one edit of the profile, with no change to the harness.

## Alternatives rejected

- Building the brief in code, or a template language with conditional sections - wording scattered across literals, or a second language for a handful of variants.
- Passing every steer into the intent - most steers answer a gate and say nothing about scope.
- An `edi intent` command the worker calls, or rewriting from an event follower - a new endpoint for what one file already does, or an intent that changes after the pipeline started.
- Counting the captain's intent - notes only grow, so a task could become impossible to dispatch, and refusing it would refuse the captain's words.

## Consequences

- Good: the template leaves room within the 120 lines AT-35 counts, a spec that would push a brief past them is refused when it is written rather than found by a test, and AT-34 reads the very file the worker passes.
- Bad: a note recorded after a pipeline run started does not reach that run, and the intent travels as one argument, fine for a task's spec but not for fleet-sized data.
- Bad: a written brief can still run past 120 lines on the captain's intent or a research plan, which the count leaves out.

## When to revisit

When the pipeline reads its intent from a file or standard input, or lets a running run take a new intent.

## How we got here

- 16/10/2026: slice S3 built the brief from one template; slice S5 had EDI write the pipeline intent with the craft rules verbatim.
- 17/10/2026: slice S6 made the worked line depend on the machine profile.
- 22/10/2026: reviewers flagged requirements the spec stated as scope creep, so the intent carries the spec and follows every note (EDI-30).
- 23/10/2026: the decision-log rule in the brief was limited to architecture and design decisions (EDI-42).
- 23/10/2026: the intent tells the pipeline's agents that the work key is put in front of their commit summaries and title, so they leave it out (EDI-21).
- 26/10/2026: only a test held the brief to 120 lines, and three of 70 written briefs ran past them, so a dispatch now refuses an overlong brief, counting neither the captain's intent nor a research plan (EDI-79).
- 27/10/2026: the pipeline command skips the steps the project registry decides, and the brief has the worker merge the base in before each run instead of letting the pipeline rebase (EDI-104).
