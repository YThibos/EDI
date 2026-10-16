# ADR-050: How the dispatch rules are read, checked and routed

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-025 settled eight dispatch categories and the EDI project override.
Section 8.6 checks the rules on load: effort in `{low, medium, high}`, absent only for models that ignore it, a reason for every category, model ids from an allow-list in the machine profile, and, with research before implementation switched on, a researcher at least as strong as the implementer (I-14, decision E22).
A relaunch re-resolves model and effort from the category; the dead worker's are advisory only, and the captain's explicit per-task instruction outranks both (I-16).
No routing may look at budget (I-17).

**Decision:** `config/dispatch-rules.json` (schema `edi-dispatch.v2`) is read on every dispatch and every launch by `DispatchRulesFile`, and `config/machine.yaml` gives the allow-list under `workers.models`, strongest first, with `ignoresEffort: true` for a model that takes no effort flag (`MachineProfile`, `ModelRoster`).
`DispatchRules.validatedAgainst` refuses, naming the file: an effort that is not low, medium or high (`EffortLevel` has no value above high), a category or override without a why, a model the roster does not allow, an effort missing for a model that takes one or given to one that ignores it, a repeated category, and any `researchFirst` category while the `research` model ranks below the `implementation` model.
`DispatchRules.routeFor(projects, category, captainsOverride)` answers the route: the captain's override when the dispatch records one, else the first project override that covers one of the task's projects, else the category's own route; it takes nothing else.
`edi dispatch` records the category, the spec and the captain's override with its reason on the task's `dispatch` row, and every launch, the first and every later one, resolves the route again from that row and the rules as they are then; the worker row keeps the route it ran on.
A dispatch of a task dispatched before may omit the category and keeps the recorded one.
Each category may name the kind of branch its work takes (`branch`, default `feature`); `examples/edi-home/config/dispatch-rules.json` carries the eight categories with `research` on research branches and `operations` and `mechanical` on chore branches.

**Motivation:** The rules stay a file the captain reads and edits, an edit takes effect at the next launch without a restart, and a relaunch can never inherit a model from a worker that ran on old rules.
The allow-list's order is the only strength ranking the research-first check needs, so no model family is hard-coded.

**Alternatives considered:**
- Validating once at start-up - an edit would need a restart, and a broken file would stop the core instead of refusing one dispatch.
- Ranking models by name - a new model generation would need a code change.
- Storing the resolved route on the dispatch - a relaunch would keep the old model, against I-16.

**Consequences:**
- Good: AT-14 to AT-17 test the loader, the EDI override, the relaunch and the resolver's signature in milliseconds.
- Bad: each dispatch reads two small files.
- Bad: the research-first policy itself, which queues a research task in front of a ship, arrives with slice S6; S3 only checks its configuration.

**Revisit when:** The research-first pilot reports (ADR-026), or the allow-list needs more than an order and an effort flag.

**Sources:** EDI rewrite specification, section 8.6 and I-14 to I-17 (decisions E13, E21, E22); ADR-025, ADR-026; `domain.dispatch`, `adapter.home.DispatchRulesFile`, `MachineProfile`, `application.dispatch.DispatchTask`, `LaunchWorker`, `examples/edi-home/config/dispatch-rules.json`, `examples/edi-home/config/machine.yaml`.
