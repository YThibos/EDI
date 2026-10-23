# Research before implementation is a switch that queues a planning scout, piloted and measured before it becomes the rule

**Kind:** Design

## Context

The captain wondered whether a researcher on a stronger model should always plan before a weaker implementer builds, and asked for the idea to be explored rather than imposed.
Work in `implementation-unplanned`, and bugs with an unknown cause, have no settled plan, while the other categories and the EDI project do not need one.
Task costs and the pipeline's review records already exist for each cleaned-up task ([cleanup](cleanup.md)).

## Decision

`researchFirst: true` is accepted only on `implementation-unplanned` and `bugfix`, and `edi dispatch --category bugfix --cause-unknown` says a bug's cause is not established; the project registered as EDI is exempt by name, and a researcher weaker than the implementer is refused ([dispatch-routing](dispatch-routing.md)).
At a ship's first dispatch, `DispatchRules.researchFirstFor` decides; when research comes first, `ResearchBeforeImplementation` records in one transaction a scout `Plan: <title>` with the ship's intent, project, key and base, the ship waiting for it, the ship's dispatch as `implementation`, and `ResearchQueuedFirst`, and prepares the scout's dispatch so a scout that cannot be dispatched refuses the whole dispatch.
The scout is asked for numbered steps naming files, classes and tests, the full acceptance criteria and how each is verified.
The ship's later dispatch waits until the scout is done and its report exists, and every launch of the ship opens its brief's Spec section with that report verbatim ([worker-brief](worker-brief.md)).
Every ship dispatched with unplanned work in an eligible category is recorded in `unplanned_ship`, with or without its research task, and cleanup records each ship's review rounds and first-round findings (`ReviewCounted`).
`ResearchFirstPilot` follows the event log, and once ten planned ships have finished it raises one decision on the captain item `research-first-pilot` with both groups' averages of tokens, review rounds, findings and review fixes, recommending the rule only when planned ships needed no more review or rework.

## Why

The policy is built as a switch, measured, and decided by the captain with numbers; the comparison uses only records EDI keeps anyway.
The plan reaches the implementer in the brief section kept for build instructions, and nothing new is needed to queue, block or launch either task.
Quality decides the recommendation and tokens are shown beside it, since quality is never traded for cost.

## Alternatives rejected

- Making it the default unmeasured, or leaving it out - the captain asked for it to be explored.
- Starting the ship automatically when the plan is written - nobody could look at the plan first.
- Alternating the switch between eligible ships - the switch would no longer mean what it says.

## Consequences

- Good: a piloted ship is an ordinary queued task blocked by an ordinary scout, visible everywhere.
- Bad: piloted ships take an extra task and an extra dispatch, and with the switch on from the first day the pilot has no baseline and recommends switching it off.

## When to revisit

When the captain answers the pilot's decision.

## How we got here

- 15/10/2026: the specification made research first a piloted switch.
- 17/10/2026: slice S6 built the queued scout, the plan in the ship's brief and the pilot.
