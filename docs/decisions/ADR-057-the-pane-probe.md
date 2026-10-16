# ADR-057: The pane probe reads Claude Code's screen in one place, from captured fixtures

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** `TmuxPaneProbe` is the only code that reads a worker pane: it reads the last 30 lines and answers `idle-prompt`, `busy`, `dialog-limit-wait`, `dialog-spend-cap`, `dialog-continue-after-reset`, `dialog-trust`, `dialog-permission` or `unknown`, from fixtures captured from real panes in slice S3 (section 12.6), plus the background-task exit picker (U-09), reading the composer rather than footer text (U-14).
It is used only before a key is sent and by `edi worker peek`; no health decision depends on it.
EDI sends only literal text plus Enter after a positive idle probe, or Escape (I-28).

**Decision:** The probe drops trailing blank lines, reads the last 30 lines, treats a no-break space as a space, and checks, in order: the exit picker ("Background work is running" with "Exit and stop tasks"), the trust dialog, the spend cap ("monthly spend limit"), the limit wait ("Wait for limit to reset", "Wait here, then continue automatically"), continue after reset ("to continue after reset"), a permission dialog (a "Do you want to ...?" line with "Esc to cancel"), busy ("esc to interrupt"), and the idle prompt: a line that is only `❯` between two rule lines of `─`, the upper of which carries the session's name when the session has one, as every EDI worker does.
Anything else, including a composer that holds text, is `unknown`.
Ten fixtures in `edi-core/src/test/resources/pane-probe/` were captured from Claude Code 2.1.301 on private tmux sockets with workspace paths replaced, one of them from a real worker in the live smoke of this slice; the three limit dialogs, which cannot be provoked on demand, were assembled from the dialog texts the 2.1.301 executable contains, and the fixtures' README says which is which.
A key reaches a pane only through `WorkerRuntime.press(window, Keystroke)`: `Keystroke` is either a `Line` of literal text without any control character, which `TmuxClaudeRuntime` types with `send-keys -l` and Enter only after the probe answers `idle-prompt`, or `Escape`; `Tmux`'s key methods are visible to the runtime alone.

**Motivation:** Claude Code changes its screen often, so its knowledge sits in one class with one fixture per shape it has met, and an unknown screen is never mistaken for an idle one.
A finished turn's summary line ("Cooked for 2s") and an idle footer without "? for shortcuts" both occur in real panes, so neither decides idleness.

**Alternatives considered:**
- Reading the footer's mode text - it changes with the permission mode and with background shells.
- Synthetic fixtures for every shape - they would test the probe against its own assumptions.

**Consequences:**
- Good: AT-28 proves on a real tmux server that a dialog takes no line and that `Enter` typed as text stays text.
- Bad: the three reconstructed dialogs are only as faithful as the texts in the executable; a real capture should replace each when it occurs.

**Revisit when:** Claude Code changes a screen the probe reads, or a limit dialog is captured live.

**Sources:** EDI rewrite specification, sections 12.5, 12.6 and 21.3 (U-09, U-14), I-28; `adapter.runtime.TmuxPaneProbe`, `TmuxClaudeRuntime`, `Tmux`, `domain.worker.Keystroke`, `PaneState`, `edi-core/src/test/resources/pane-probe/`.
