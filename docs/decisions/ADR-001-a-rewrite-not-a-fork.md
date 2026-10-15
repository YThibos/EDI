# ADR-001: EDI is a rewrite, not a fork

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** EDI replaces a bash harness that was itself a fork of the open-source firstmate harness.
That harness grew to about 108,000 lines of bash, 217 scripts and 269 test files, with an always-loaded anchor of 40 KB at its ceiling.
Its rules lived inside scripts that call tmux, git and Claude Code directly, so testing a rule meant starting real processes, and its suites took hours, leaked processes and flaked under load.
It also carried generality its one user never needed: other agent harnesses, other session backends, relays, second mates and upstream synchronisation.

**Decision:** EDI (Enhanced Development Intelligence) is a full rewrite decoupled from upstream, keeping the operating model the old harness taught and none of its code.
The operating model is one conversational interface, explicit authority, refusal over guessing, never losing unlanded work, and durable records that make a restart a non-event.

**Motivation:** The captain's direction: "I am going to fully rewrite the harness and decouple from the fork."
A rewrite lets the safety rules become small, typed and testable in milliseconds, which the old structure could not offer at any reasonable cost.

**Alternatives considered:**
- Keep the fork and prune it - the rules would still be tangled with process calls, so the test cycle would stay hours long.
- Keep following upstream - upstream serves many setups; its generality is cost without benefit for one developer on two machines.

**Consequences:**
- Good: every rule EDI keeps is stated once, in Java, with a fast test (ADR-007, ADR-034).
- Good: no upstream merge work and no inherited subsystems.
- Bad: the old harness keeps running the work until EDI reaches parity, so two harnesses coexist for a while.
- Bad: lessons that lived only in the old code must be carried over deliberately; the specification's port ledger does that.

**Revisit when:** Never for the direction itself; individual features are revisited through their own records.

**Sources:** EDI rewrite specification, sections 1 and 22 (decision D-dir, settled 15/10/2026).
