# ADR-099: Research before implementation queues a research scout and runs the ship as implementation with its plan

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-026 builds research before implementation as a switch per category: with `researchFirst: true` on `implementation-unplanned`, and on `bugfix` when the cause is unknown, a dispatch first creates a `research` task whose deliverable is an enumerated plan with acceptance criteria, and the ship is queued behind it and runs as `implementation` with the plan in its brief.
`mechanical`, `review-fix`, `documentation`, `operations` and the EDI project are exempt; ADR-050 already refuses a researcher weaker than the implementer.

**Decision:** The rules accept `researchFirst` only on `implementation-unplanned` and `bugfix`, the two categories whose work has no settled plan or cause, so the exempt categories cannot switch it on; the project registered as EDI is left out by name, as section 8.6a exempts it, and a project override on any other project keeps research first and the pilot.
`edi dispatch <ship> --category bugfix --cause-unknown` says the cause is not established; any other category with that flag is refused.
At a ship's first dispatch, `DispatchRules.researchFirstFor` decides; when research comes first, `ResearchBeforeImplementation` records in one transaction a scout `Plan: <title>` with the ship's intent, project, key and base, the ship waiting for it, the ship's dispatch as `implementation` on its own branch, and `ResearchQueuedFirst`, and in that same transaction prepares the scout's dispatch as `research` on its research branch, so a scout that cannot be dispatched, such as on a project whose registry allows no research branches, refuses the whole dispatch and leaves the ship as it was; the scout is then launched with a spec asking for numbered steps naming the files, classes and tests, the full acceptance criteria and how each is verified, followed by the ship's own build instructions.
The ship's later dispatch needs no category, waits until the scout is done, and refuses while the scout's report is missing; its brief's Spec section opens with the scout's report verbatim, and every launch of the ship, relaunches included, reads it again.

**Motivation:** The plan reaches the implementer in the one section the brief keeps for build instructions, the captain or the primary can read the plan before the ship starts, and nothing new is needed to queue, block or launch either task.

**Alternatives considered:**
- Starting the ship automatically when the plan is written - nobody could look at the plan first, and a needs-decision in the plan would be skipped.
- A separate Plan section in the brief - the brief's sections are fixed by section 10.4, and the plan is the ship's build instructions.

**Consequences:**
- Good: a piloted ship is an ordinary queued task blocked by an ordinary scout, visible in the backlog and the report.
- Bad: the primary dispatches the ship once more after cleaning up the scout.

**Revisit when:** The pilot decides whether research first becomes the rule (ADR-100).

**Sources:** EDI rewrite specification, sections 8.6 and 8.6a (decisions E21, E22); ADR-025, ADR-026, ADR-050, ADR-055; `domain.dispatch.DispatchRules`, `ResearchFirst`, `CategoryId`, `Dispatch`, `application.dispatch.DispatchTask`, `ResearchBeforeImplementation`, `LaunchWorker`, `application.brief.BriefSections`, `web.dispatch.DispatchBody`, `edi-cli` `DispatchCommand`, `examples/edi-home/config/dispatch-rules.json`.
