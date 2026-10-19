# ADR-111: Research boards are armed through EDI and listened to by the core

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** Section 15.5: a scout writes its research board in its task directory and runs `edi board arm <html> --task <id> [--bind D..]`; the core owns the `lavish-axi poll` child, restarts it if it dies, and turns each result into an inbox message for the owning worker and an answer for each bound decision.
Section 9.1 puts `board arm` in the worker class, for the worker's own task; section 11.8 has cleanup retire any board listener of the task, which ADR-090 left to this slice.
`ResearchBoardHost` is one of the six ports (ADR-007), empty until now.
`lavish-axi poll <file>` long-polls without end until the captain sends feedback, ends the session or leaves the review window, takes `--owner <label>` to name its listener and `--takeover` to displace another one, and prints its result as TOON.

**Decision:** `ResearchBoardHost.awaitFeedback(board, listener)` blocks until one round of feedback and returns a `BoardFeedback`: answered (with its prompts and whether the session ended), closed without content, disconnected, or failed.
`LavishBoardHost`, in the new package `adapter.board`, owns the program `LAVISH` and runs `lavish-axi poll <file> --owner edi:<task>#<board> --takeover` through `ExternalPrograms.runUntilItEnds`, which waits without a time limit and stops the program's process tree when its thread is interrupted; `edi.boards.lavish` names another executable.
`ArmBoard` refuses a board that is not an existing HTML file inside the task's directory, a worker arming for another task, and a decision that is not open; arming a file that already listens rebinds it instead of listening twice.
`BoardListeners` runs one listener per listening board on a virtual thread: after an answered round it polls again at once, and after anything else it stops; a round every `edi.boards.round` (PT30S by default) starts a listener for every listening board without one, so a listener that died or a core that restarted is listening again within a round, and it stops the listener of every board that no longer listens.
Cleanup retires the task's listening boards in its own transaction (`BoardRetired`) and stops their listeners at once (`BoardListeners.forget`).
A failure Lavish reports is logged once per board until it changes.

**Motivation:** The port keeps every rule about boards testable with a fake host, and `--takeover` lets a restarted core take its boards back from a poll an earlier core left behind.

**Alternatives considered:**
- A listener the scout runs itself - its answers die with its session, as they did on the old harness.
- Restarting a stopped listener at once - a session Lavish keeps disconnected would spin the poll.
- Arming boards anywhere in the worker's copy - the specification places them in the task directory, which outlives the copy and holds the scout's report.

**Consequences:**
- Good: the captain's answers on a board reach EDI whether or not the scout is alive (ADR-113).
- Bad: one `lavish-axi poll` process runs per listening board for as long as the board listens.

**Revisit when:** Lavish offers a structured output, or more than a handful of boards listen at once.

**Sources:** EDI rewrite specification, sections 9.1, 11.8, 12.1 and 15.5; ADR-007, ADR-009, ADR-090; `application.port.ResearchBoardHost`, `adapter.board.LavishBoardHost`, `adapter.process.ExternalPrograms`, `application.board.ArmBoard`, `BoardListeners`, `application.delivery.CleanUpTask`, `web.board.BoardController`, `edi-cli` `BoardArmCommand`.
