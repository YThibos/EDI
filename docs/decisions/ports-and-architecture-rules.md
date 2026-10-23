# Exactly six ports, and the architecture rules are tests

**Kind:** Architecture

## Context

EDI has one user, one bounded context and a small domain; its real difficulty is the dozen external tools it drives and the safety rules that must hold whatever those tools do.
Two of those integrations differ per machine, and several cannot run inside a fast test.
Every incident in the old harness came from a safety rule that drifted, and a rule written only in prose drifts.
ArchUnit checks package dependencies, calls, field access and annotations of compiled classes, but cannot read string constants.

## Decision

A light ports-and-adapters split, nothing more.
The `domain` package holds plain Java: entities with behaviour where a rule lives, and value objects that make invalid states impossible to build.
Ports are interfaces in `application.port` at exactly two kinds of boundary: where two production implementations exist (`Forge`, `Tracker`) and where the real thing cannot run inside a fast test (`WorkerRuntime`, `Pipeline`, `ResearchBoardHost`, `Clock`).
`PortInventoryTest` counts them, and a seventh port needs a new decision record.
Everything else is a concrete class used directly: the store, git, the ledger writer, the notifier and the controllers.

`ArchitectureRulesTest` checks on every build, over hand-written classes only (`HandWrittenClasses`), that:

- the domain depends only on itself and the core Java library, and touches no process, system, thread or reflection API;
- only `adapter.process` starts programs, each external program is a constant of the closed enum `Program`, and each constant is used only by the adapter that owns it, such as `GIT` by `adapter.git` and `GH` and `GLAB` by `adapter.forge`;
- every time read and every read of the machine's time zone goes through the `Clock` port, outside `adapter.clock` ([time-zones](time-zones.md));
- no class or method names a merge operation (`NameAMergeOperation`), which `CommandArchitectureRulesTest` applies to `edi-cli` too ([delivery](delivery.md));
- nothing loads classes at runtime, and nothing chooses machine differences at build time;
- every interface in `application` is a port, only adapters implement ports, adapters reach the application only through ports, and nothing depends on the web layer.

## Why

The safety rules must be testable in milliseconds without a process in sight, and every volatile integration must change in one adapter instead of rippling through services; six ports are the smallest structure that gives both.
A rule that fails the build cannot drift, and a closed enum of programs gives ArchUnit something it can see, so "who runs what" is a checked fact.

## Alternatives rejected

- Full domain-driven design with aggregates and repositories everywhere - too much ceremony for one small domain.
- A transaction-script application calling processes from services - rules testable only by starting real tools, the old harness's hours-long, flaky cycle.
- Searching source text for program names, or review-only rules - text matching proves nothing about behaviour, and prose rules drift.

## Consequences

- Good: the port count cannot creep, and there are no repository interfaces, mapping layers or interfaces with one implementation.
- Bad: adding an external program means adding a `Program` constant and its owner, which is deliberate friction.

## When to revisit

When a third production implementation or a new untestable integration appears, or a rule blocks a legitimate design; either is a change to this record, not a quiet exception.

## How we got here

- 15/10/2026: the captain asked whether hexagonal architecture and domain-driven design suit the harness without creating complexity; slice S0 declared the six ports, empty, so the inventory held from the first commit, and each later slice gave its port operations with their first caller.
- 22/10/2026: the time zone joined the `Clock` port and its rule (EDI-33).
