# ADR-056: A steer waits in the worker's inbox until a hook proves it was received

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** A message to a worker counts as delivered only when the worker's hook acknowledges it (I-27).
Section 12.4: a busy worker gets the message at its next stop, whose hook blocks the stop with a notice; an idle worker gets one typed line after the pane probe shows the idle prompt, and the `UserPromptSubmit` hook carrying that line is the proof; a message unacknowledged after five minutes raises one "steer not received".
EDI never presses a key into a dialog (I-28).

**Decision:** `edi steer <task> [--resolves KEY]` stores message `<n>` for the task's serving worker in `inbox_message` (`SteerWorker`) and, with `--resolves`, records the `resolved` status for that key.
Delivery follows the worker's activity, which its hooks keep: busy from `UserPromptSubmit` to `Stop`, idle after `Stop` (`WorkerHooks`).
A busy worker gets nothing typed; its next `Stop` hook marks every unreceived message received and blocks the stop once with "A message is waiting: run edi inbox, then act on it before you stop.".
An idle worker gets the line `EDI_OP: message <n> waiting: run edi inbox` typed only when the runtime's probe shows the idle prompt; the `UserPromptSubmit` hook whose prompt carries that line marks the message received.
`edi inbox` also marks what it shows as received, and `edi inbox ack <n>` acknowledges a message once the worker acted on it.
`edi steer` answers how the message will arrive: typed into the idle prompt, at the next stop, waiting because the pane shows something else, or waiting because the worker's window is closed.
Every five-minute-old message that no hook and no read has received raises `SteerNotReceived` once (`NoticeOverdueSteers`, ADR-061).
A typed line carries the visible marker `EDI_OP: ` without the invisible separator U+2063 that section 8.9 puts in front of text EDI injects (`OperationalText.typed`): in the live smoke of this slice, Claude Code 2.1.301 never submitted a typed line that began with U+2063, however long the wait before Enter, and submitted the same line without it at once; the first prompt, which travels as an argument, keeps the full marker (`OperationalText.marked`).

**Motivation:** Delivery is proved by the worker's own session, never assumed from a keystroke, and a dialog in the pane is never typed into.
Receipt rather than acknowledgement decides "steer not received", because a worker that read its message has received it even before it finished acting on it.

**Alternatives considered:**
- Typing the notice whenever the worker looks idle by its hooks alone - a dialog opened after the last stop would take the keystrokes.
- Counting a delivery as proved when the keys were sent - the old harness's failure class.
- Typing the full marker with U+2063 - the composer keeps the line and never submits it.

**Consequences:**
- Good: AT-27 runs in milliseconds with a controllable clock, and the native smoke test steers a real pane.
- Bad: a message to an idle worker whose pane shows a dialog waits until the worker stops again or "steer not received" is raised.
- Bad: a reader that excludes EDI's injected text by its marker, such as the time-booking tool, must accept `EDI_OP: ` with and without U+2063.

**Revisit when:** The primary's wake digest (slice S4) turns "steer not received" into a wake, or Claude Code offers a way to push a message without a prompt.

**Sources:** EDI rewrite specification, sections 10.2, 12.4 and I-27, I-28; `application.worker.SteerWorker`, `WorkerHooks`, `WorkerInbox`, `NoticeOverdueSteers`, `domain.inbox.InboxMessage`.
