# ADR-035: What EDI deliberately does not build

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** The old harness carried generality for other people's setups.
Each candidate was checked against real use on both machines, and the captain dropped everything except away and quiet modes.

**Decision:** EDI does not build:
- agent harnesses other than Claude Code, since every dispatch category names a Claude model;
- session backends other than tmux;
- second mates, remote homes, parent channels or charters;
- relays through social networks, chat, mail or voice;
- quota-aware profile arrays, since fixed models with three effort levels suffice;
- Gerrit support;
- upstream synchronisation;
- the old `/bearings` and `/ahoy` commands, absorbed by `/sitrep` and the session-start digest (ADR-024);
- any merge capability (ADR-022);
- macOS support, since both machines run WSL2 Ubuntu;
- an EDI-native work board in version 1, though the console and the tracker seam are designed for it.

**Motivation:** Nothing on the list is used today; each would be code to test and keep current for no benefit.

**Alternatives considered:**
- Porting the old harness's subsystems as they were - the generality that made it 108,000 lines of bash.

**Consequences:**
- Good: a much smaller harness.
- Bad: adding any of these later is a new design, not a port.

**Revisit when:** A real use for one of them appears; the work board is the one already expected.

**Sources:** EDI rewrite specification, sections 1.3 and 14.4 (decision E15).
