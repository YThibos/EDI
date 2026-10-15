# ADR-007: Ports only where they pay: exactly six

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** The captain asked whether hexagonal architecture and domain-driven design suit the harness: "I'm not against, but I don't want to give ourselves complexity problems in the future."
EDI has one user, one bounded context and a small domain; its real difficulty is the dozen external tools it drives (Claude Code, tmux, git, GitLab and GitHub command-line tools, the validation pipeline, JIRA, the research-board tool) and the safety rules that must hold whatever those tools do.
Two of those integrations differ per machine, and several cannot run inside a fast test.

**Decision:** A light ports-and-adapters split, nothing more.
The `domain` package holds plain Java: entities with behaviour where a rule lives and value objects that make invalid states impossible to build.
Ports are interfaces in `application.port` at exactly two kinds of boundary: where two production implementations exist (`Forge`, `Tracker`) and where the real thing cannot run inside a fast test (`WorkerRuntime`, `Pipeline`, `ResearchBoardHost`, `Clock`).
That is six ports, counted by `PortInventoryTest` (AT-45); a seventh needs a new decision record.
Everything else is a concrete class used directly: the store, git operations, the fleet ledger writer, the notifier and the controllers.
Slice S0 declares the six port interfaces so the inventory holds from the first commit; each later slice gives its port its operations together with their first caller.

**Motivation:** The safety rules must be testable in milliseconds without a process in sight, and every volatile integration must change in one adapter instead of rippling through services.
Six ports are the smallest structure that delivers both.

**Alternatives considered:**
- Full domain-driven design with aggregates and repositories everywhere - too much ceremony for one small domain.
- A plain transaction-script application calling processes from services - fewer classes on day one, but rules testable only by starting real tools, which recreates the old harness's hours-long, flaky test cycle.

**Consequences:**
- Good: the port count cannot creep, because the inventory test and the rule that every application interface is a port fail first.
- Good: no repository interfaces, mapping layers or interfaces with one implementation.
- Bad: until their slices land, five of the six ports are empty interfaces whose only purpose is to fix the inventory.

**Revisit when:** A third production implementation or a new untestable integration appears; that is a new decision record, not a quiet seventh port.

**Sources:** EDI rewrite specification, section 5.3 (decision E2, AT-45); `edi-core/src/main/java/io/github/ythibos/edi/application/port/`, `PortInventoryTest`, `ArchitectureRulesTest.everyApplicationInterfaceIsAPort`.
