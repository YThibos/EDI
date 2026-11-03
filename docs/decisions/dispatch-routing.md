# Work is dispatched by a few written categories, read afresh at every launch, within each project's capacity

**Kind:** Design

## Context

The old harness routed work with sixteen rules and a default, with inconsistencies found in review.
Effort has five levels, `low` to `max`, and `high` is EDI's ceiling (I-14), which only the captain's recorded word for one task lifts, EDI's own research and implementation run on Opus 5.5 at high (I-15), a relaunch resolves its model and effort from the category as a fresh dispatch does, with the captain's explicit instruction above both (I-16), and no routing looks at budget (I-17).
Some projects' builds load the machine, such as Testcontainers suites, so running many of their workers at once hurts.

## Decision

`config/dispatch-rules.json` (schema `edi-dispatch.v2`) holds the categories, each with a model, an effort and a written reason, project overrides above them, and the `pipeline` entry that routes the validation pipeline's agent ([pipeline-agent](pipeline-agent.md)); `examples/edi-home/config/dispatch-rules.json` ships eight categories: `research`, `implementation` with a settled plan, `implementation-unplanned`, `bugfix` with an established cause, `documentation`, `operations`, `mechanical` and `review-fix`.
`DispatchRulesFile` reads it at every dispatch and every launch, and `MachineProfile` gives the allowed models under `workers.models`, strongest first, with `ignoresEffort` for a model that takes no effort flag.
A project override may name the categories it covers in `categories`, and one without covers every category of its project; the example's EDI override covers `research`, `implementation`, `implementation-unplanned` and `bugfix` and runs them on Opus 5.5 at high, so EDI's other work routes by its category (`ProjectOverride`).
`DispatchRules.validatedAgainst` refuses, naming the file, an effort that is not low, medium or high, a category or override without a reason, an override covering a category the rules do not hold, a model the roster does not allow, an effort missing for a model that takes one or given to one that ignores it, a repeated category, and a researcher ranked below the implementer when research comes first ([research-first](research-first.md)).
`DispatchRules.routeFor(projects, category, captainsOverride)` answers the captain's override when the dispatch records one, else the first project override covering one of the task's projects and its category, else the category's route, and takes nothing else.
`EffortLevel` knows the five levels Claude Code's `--effort` takes, `low` to `max`; the rules file and an outage recovery read effort with `EffortLevel.withinTheCeiling`, so `xhigh` and `max` never land in a category, a project override or the pipeline entry.
A captain's override above `high` (`DispatchRequest.liftsTheEffortCeiling`) is accepted only with its reason and the captain's word recorded on that task for that act in the current exchange with `edi captain said --task <task> --for effort-above-high`, or a dialog answer whose chosen option opens with `effort-above-high` and names the task and no other (`DispatchTask`, `CaptainWordAct.EFFORT_ABOVE_HIGH`, [captains-word](captains-word.md)).
`edi dispatch` records the category, the spec and the captain's override on the task's `dispatch` row, and every launch resolves the route again from that row and the rules as they are then; the worker row keeps the route it ran on.
Each category may name its branch kind ([work-keys-and-branches](work-keys-and-branches.md)), and `mechanical` carries its own checks ([mechanical-tasks](mechanical-tasks.md)).
A project may set `capacity: <n>`; a dispatch beyond it records its deferral and leaves the task queued (`DispatchDeferred`), and each supervision round launches deferred dispatches, oldest first, while their project has room (`WorkerPlaces`, `LaunchDeferredDispatches`), each in its own attempt.

## Why

A few categories the primary can pick by judgement, one token-saving category made safe by mechanical checks, and overrides that keep a project's named kinds of work on a standing order, as the override's own reason states it.
An override without `categories` keeps a whole-project standing order a one-line rule.
The rules stay a file the captain reads and edits, an edit takes effect at the next launch, and a relaunch never inherits a model from rules that changed.
The roster's order is the only strength ranking the checks need, so no model family is hard-coded, and deferral is state in the store, so it survives a restart.

## Alternatives rejected

- Third-party automatic routing - as few third-party services as possible ([open-source-tools](open-source-tools.md)).
- Keeping the sixteen rules - hard to apply consistently.
- Validating once at start-up, or storing the resolved route - an edit would need a restart, and a relaunch would keep an old model.
- Refusing a dispatch beyond capacity - the primary would have to remember to dispatch it again.
- An override above every category of its project - EDI's documentation and operations ran on Opus at high, against the override's own reason and I-15.
- Excluding categories from an override instead of naming them, or EDI-only categories - the rule would no longer name the work its reason names, or the primary would pick a different category for the same work depending on the project.
- Allowing `xhigh` or `max` in the rules or a project override, or keeping a lifted ceiling anywhere but the task's own `dispatch` row - an exception for one task would become a standing order.

## Consequences

- Good: AT-14 to AT-17 test the loader, the overrides, the relaunch and the resolver's signature in milliseconds; AT-15 checks that EDI's research and implementation take the override and its other work its category.
- Good: a misspelt category in an override is refused when the rules are read, never silently ignored.
- Bad: the primary's choice of category is judgement, not a rule, and each dispatch reads two small files.
- Bad: a freed place is used up to one supervision round later.

## When to revisit

When the research-first pilot reports, a model generation changes, or a cap across all projects is wanted.

## How we got here

- 15/10/2026: the captain asked to compact the routing into a few simple categories, with cheaper models where that is foolproof; the specification settled eight and a project override.
- 16/10/2026: slice S3 built the loader, the checks, the resolver and project capacity.
- 23/10/2026: the unscoped EDI override had also put EDI's documentation and operations on Opus at high; the captain asked to narrow it to research and implementation, as its reason says, so an override names the categories it covers (EDI-46).
- 26/10/2026: the captain asked for one researcher at `xhigh`; their recorded word on a task now lifts that task alone above `high`, through its relaunches, and nothing else (EDI-49).
- 26/10/2026: the lift now needs the captain's word recorded on the task for that act, `--for effort-above-high`, never a general word on it (EDI-49).
- 02/11/2026: a dialog answer that opens with `effort-above-high` and names the task lifts it as a recorded word does (EDI-159).
