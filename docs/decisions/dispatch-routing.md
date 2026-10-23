# Work is dispatched by a few written categories, read afresh at every launch, within each project's capacity

**Kind:** Design

## Context

The old harness routed work with sixteen rules and a default, with inconsistencies found in review.
Effort has three levels with `high` as the ceiling (I-14), a relaunch resolves its model and effort from the category as a fresh dispatch does, with the captain's explicit instruction above both (I-16), and no routing looks at budget (I-17).
Some projects' builds load the machine, such as Testcontainers suites, so running many of their workers at once hurts.

## Decision

`config/dispatch-rules.json` (schema `edi-dispatch.v2`) holds the categories, each with a model, an effort and a written reason, and project overrides above them; `examples/edi-home/config/dispatch-rules.json` ships eight: `research`, `implementation` with a settled plan, `implementation-unplanned`, `bugfix` with an established cause, `documentation`, `operations`, `mechanical` and `review-fix`.
`DispatchRulesFile` reads it at every dispatch and every launch, and `MachineProfile` gives the allowed models under `workers.models`, strongest first, with `ignoresEffort` for a model that takes no effort flag.
`DispatchRules.validatedAgainst` refuses, naming the file, an effort that is not low, medium or high, a category or override without a reason, a model the roster does not allow, an effort missing for a model that takes one or given to one that ignores it, a repeated category, and a researcher ranked below the implementer when research comes first ([research-first](research-first.md)).
`DispatchRules.routeFor(projects, category, captainsOverride)` answers the captain's override when the dispatch records one, else the first project override covering the task's projects, else the category's route, and takes nothing else.
`edi dispatch` records the category, the spec and the captain's override on the task's `dispatch` row, and every launch resolves the route again from that row and the rules as they are then; the worker row keeps the route it ran on.
Each category may name its branch kind ([work-keys-and-branches](work-keys-and-branches.md)), and `mechanical` carries its own checks ([mechanical-tasks](mechanical-tasks.md)).
A project may set `capacity: <n>`; a dispatch beyond it records its deferral and leaves the task queued (`DispatchDeferred`), and each supervision round launches deferred dispatches, oldest first, while their project has room (`WorkerPlaces`, `LaunchDeferredDispatches`), each in its own attempt.

## Why

A few categories the primary can pick by judgement, one token-saving category made safe by mechanical checks, and overrides that keep a project on a standing order.
The rules stay a file the captain reads and edits, an edit takes effect at the next launch, and a relaunch never inherits a model from rules that changed.
The roster's order is the only strength ranking the checks need, so no model family is hard-coded, and deferral is state in the store, so it survives a restart.

## Alternatives rejected

- Third-party automatic routing - as few third-party services as possible ([open-source-tools](open-source-tools.md)).
- Keeping the sixteen rules - hard to apply consistently.
- Validating once at start-up, or storing the resolved route - an edit would need a restart, and a relaunch would keep an old model.
- Refusing a dispatch beyond capacity - the primary would have to remember to dispatch it again.

## Consequences

- Good: AT-14 to AT-17 test the loader, the overrides, the relaunch and the resolver's signature in milliseconds.
- Bad: the primary's choice of category is judgement, not a rule, and each dispatch reads two small files.
- Bad: a freed place is used up to one supervision round later.

## When to revisit

When the research-first pilot reports, a model generation changes, or a cap across all projects is wanted.

## How we got here

- 15/10/2026: the captain asked to compact the routing into a few simple categories, with cheaper models where that is foolproof; the specification settled eight and a project override.
- 16/10/2026: slice S3 built the loader, the checks, the resolver and project capacity.
