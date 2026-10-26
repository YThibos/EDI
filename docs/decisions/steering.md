# A steer waits in the worker's inbox until a hook proves it arrived, and keys reach a pane only after the pane probe

**Kind:** Design

## Context

A message to a worker counts as delivered only when the worker's own session acknowledges it (I-27), and EDI never presses a key into a dialog (I-28).
Claude Code changes its screen often, and some of its dialogs, such as the usage-limit ones, cannot be provoked on demand.
In live tests Claude Code never submitted a typed line that began with the invisible separator U+2063, and submitted the same line without it at once.

## Decision

`edi steer <task> [--resolves KEY]` stores a message for the task's serving worker in `inbox_message` (`SteerWorker`).
Delivery follows the worker's activity, which its hooks keep: busy from `UserPromptSubmit` to `Stop`, idle after `Stop` (`WorkerHooks`).
A busy worker gets nothing typed; its next `Stop` hook marks the message received and blocks the stop once with a notice to run `edi inbox`.
An idle worker gets the line `EDI_OP: message <n> waiting: run edi inbox` typed only when the pane probe shows the idle prompt, and the `UserPromptSubmit` hook carrying that line marks it received; a typed line carries the visible marker without U+2063 (`OperationalText.typed`).
`edi inbox` marks what it shows as received, and `edi inbox ack <n>` acknowledges a message once acted on; a worker's done waits until every message is acknowledged ([delivery](delivery.md)); a message no hook or read received after five minutes raises `SteerNotReceived` once (`NoticeOverdueSteers`).
`TmuxPaneProbe` is the only code that reads a pane: the last 30 lines, checked in a fixed order for the background-task exit picker, the trust dialog, the three limit dialogs, a permission dialog, busy, and the idle prompt, which is a lone `❯` between two rule lines; anything else, including a composer holding text, is `unknown`.
A key reaches a pane only through `WorkerRuntime.press(window, Keystroke)`, where a `Keystroke` is a `Line` of literal text, typed with `send-keys -l` and Enter after a positive idle probe, or `Escape`.
No health decision depends on the probe.

## Why

Delivery is proved by the worker's session, never assumed from a keystroke, and a dialog is never typed into.
Receipt rather than acknowledgement decides "steer not received", because a worker that read its message has it even before it finished acting.
Claude Code's screen knowledge sits in one class, with one fixture captured from a real pane per shape it has met, so an unknown screen is never taken for an idle one.

## Alternatives rejected

- Typing whenever the hooks say idle - a dialog opened after the last stop would take the keystrokes.
- Counting a delivery once the keys were sent - the old harness's failure class.
- Reading the footer's mode text, or synthetic fixtures - the footer changes with mode and background shells, and synthetic fixtures test the probe against its own assumptions.

## Consequences

- Good: AT-27 and AT-28 run in milliseconds and on a real private tmux server, and the native smoke test steers a real pane.
- Bad: a message to an idle worker whose pane shows a dialog waits until it stops again or the overdue notice is raised.
- Bad: the three limit dialogs were assembled from texts in the Claude Code executable, and a reader that excludes EDI's text by its marker must accept it with and without U+2063.

## When to revisit

When Claude Code changes a screen the probe reads, offers a way to push a message without a prompt, or a limit dialog is captured live.

## How we got here

- 16/10/2026: slice S3 built steering with proof of delivery and the pane probe from captured fixtures, and dropped U+2063 from typed lines after the live smoke.
- 21/10/2026: an acknowledged steer that resolves a worker's question started to settle that question's status ([fleet-snapshot](fleet-snapshot.md)).
- 26/10/2026: a done stopped running ahead of a steer that waits unacknowledged (EDI-52).
