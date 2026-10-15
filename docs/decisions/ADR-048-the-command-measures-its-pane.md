# ADR-048: The command measures its pane and the core sizes the report

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The captain report is 120 columns wide, narrowed to the pane width minus the two columns Claude Code indents a message by, and never below 60 (section 15.1).
Only a process in the captain's pane knows that pane: the primary's `edi` inherits `TMUX` and `TMUX_PANE`, while the core runs in the background.
The command is a thin pipe (ADR-043), the core's own tmux server is EDI's private one (I-31), and nobody runs tmux without naming its socket.

**Decision:** `edi report` and `edi sitrep` ask tmux for the width of their own pane (`TmuxPane`), naming the server's socket from `TMUX` with `-S`, waiting at most two seconds, and send the answer as the `paneWidth` parameter; outside tmux, or when tmux does not answer, they send none.
The core applies the rule in `ReportWidth.forPane`: the pane width less two columns, kept between 60 and 120; without `paneWidth` the report is 120 columns.

**Motivation:** The pane is a property of the caller's terminal, like its size, so the caller measures it; the core never runs a program against the captain's own tmux server, and the rule stays with the rest of the layout in the core.

**Alternatives considered:**
- The core asking tmux, given the caller's socket and pane - the core would run tmux on a server that is not EDI's, at any caller's word.
- A `--width` option - the primary would have to measure its pane itself in every turn.

**Consequences:**
- Good: the report fits the captain's pane, as the old one did.
- Bad: each report starts one short tmux process in the command, a few milliseconds.

**Revisit when:** The report is read somewhere other than a tmux pane.

**Sources:** EDI rewrite specification, sections 9.1 and 15.1 (I-31); ADR-043, ADR-047; `edi-cli` `TmuxPane`, `ReportCommand`, `SitrepCommand`, `TmuxPaneTest`; `web.report.ReportWidth`.
