# EDI is a small rewrite of the old harness, and builds only what is used

**Kind:** Architecture

## Context

EDI replaces a bash harness that was itself a fork of the open-source firstmate harness.
That harness grew to about 108,000 lines of bash in 217 scripts and 269 test files, with an always-loaded anchor of 40 KB.
Its rules lived inside scripts that called tmux, git and Claude Code directly, so testing a rule meant starting real processes, and its suites took hours, leaked processes and flaked under load.
It also carried generality its one user never needed: other agent harnesses, other session backends, relays, second mates and upstream synchronisation.

## Decision

EDI (Enhanced Development Intelligence) is a full rewrite, decoupled from upstream, that keeps the old harness's operating model and none of its code.
The operating model is one conversational interface, explicit authority, refusal over guessing, never losing unlanded work, and durable records that make a restart a non-event.
EDI deliberately does not build:

- agent harnesses other than Claude Code, or session backends other than tmux;
- second mates, remote homes, parent channels, charters, or relays through chat, mail or voice;
- quota-aware profile arrays, Gerrit support or upstream synchronisation;
- a second overview command beside `/sitrep` ([captain-report](captain-report.md));
- any merge capability ([delivery](delivery.md));
- macOS support, since both machines run WSL2 Ubuntu;
- an EDI-native work board in version 1, although the console and the tracker port are shaped for one.

## Why

A rewrite lets each safety rule become small, typed Java with a test that runs in milliseconds ([ports-and-architecture-rules](ports-and-architecture-rules.md)), which the old structure could not offer at any reasonable cost.
Nothing on the list of cuts is used on either machine, and each would be code to test and keep current for no benefit.

## Alternatives rejected

- Keeping the fork and pruning it - the rules would stay tangled with process calls, so the test cycle would stay hours long.
- Following upstream - its generality serves many setups and is cost without benefit for one developer on two machines.
- Porting the old subsystems as they were - the generality is what made the old harness 108,000 lines.

## Consequences

- Good: every rule EDI keeps is stated once, in Java, with a fast test, and there is no upstream merge work.
- Bad: lessons that lived only in the old code had to be carried over deliberately, through the specification's port ledger and [old-harness-migration](old-harness-migration.md).
- Bad: adding any of the cut features later is a new design, not a port.

## When to revisit

Never for the direction itself; a real use for one of the cut features reopens that feature, and the work board is the one already expected.

## How we got here

- 15/10/2026: the captain decided to rewrite the harness and decouple it from the fork; the specification settled the scope cuts, keeping only the away and quiet modes among the old optional modes ([presence](presence.md)).
- 19/10/2026: slice S10 imported the old harness's backlog, memory and configuration, and the two harnesses ran side by side until the cutover.
