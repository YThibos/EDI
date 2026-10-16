# ADR-055: The worker brief is one template filled in from the task, its dispatch and the registry

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 10.4 sets the brief's ten sections in order, at most 120 lines before the task's own intent and spec (I-35), with the rule that a worker never addresses the captain (I-01).
Project rules come from the registry: craft rules, AI scaffolding posture, the decision log, no explanatory comments, no legacy below 1.0.0, no SNAPSHOT, and the project's own `AGENTS.md` and skills to read first.
Large-build safety lines appear only for projects the registry marks, and a paused worker sets `until` to its job's start plus its timeout (U-10).

**Decision:** `claude/worker/brief-template.md` holds every fixed sentence of the brief with named placeholders; `WorkerBriefs` fills them from `BriefFacts` (the task, the worker, the registry entry, the dispatch, the base, the paths and the craft rules text), and a line holding only a placeholder that fills in empty is dropped (`BriefTemplate`).
The task's notes are the captain's intent, recorded at intake, and the dispatch's `--spec` is the spec; `BriefSections` writes the variable sections: project rules from `aiScaffolding`, `shipBriefRules`, `craftRules` with the text of `memory/craft-rules.md` in the EDI home, and the ship rules; validation and delivery per task kind and delivery mode; the definition of done; and, for a mechanical task, the named files and its limits.
A project with `craftRules: true` and no craft rules file is refused at launch.
Each launch writes the brief to `data/tasks/<task-id>/brief.md`, and `edi brief <task>` prints it, or with `--category` the brief a dispatch would write.
AT-35 counts the brief's lines outside the intent and spec sections for every kind of brief.

**Motivation:** The wording of the brief can be read and reviewed in one Markdown file, and the code decides only which parts apply.

**Alternatives considered:**
- Building the brief in code - the wording would be scattered across string literals.
- A template language with conditional sections - a second language for a handful of variants.

**Consequences:**
- Good: the largest brief, a mechanical ship with craft rules, keeps 89 lines of its own around the task's sections, within the 120 of AT-35.
- Bad: a project's skills outside the repository, which section 8.4 adds through `.git/info/exclude` for `aiScaffolding: local-only`, are named only as a rule; linking them arrives with the registry field that names them.

**Revisit when:** The pipeline intent becomes a generated artefact with slice S5, or a section needs wording per project.

**Sources:** EDI rewrite specification, sections 10.4, 8.4, 13.5 and I-01, I-35, U-10; `application.brief.WorkerBriefs`, `BriefTemplate`, `BriefSections`, `BriefFacts`, `WrittenBriefQuery`, `claude/worker/brief-template.md`.
