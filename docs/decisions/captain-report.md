# The boxed captain report keeps the old renderer's layout, sized to the caller's pane

**Kind:** Design

## Context

The captain confirmed the old harness's boxed report design, and the specification requires `CaptainReportPresenter` to reproduce the old renderer byte for byte on the same inputs, with its test cases as golden files (AT-42).
The old harness had two overview commands, and only `/sitrep` was kept.
The report is 120 columns wide, narrowed to the pane width less the two columns Claude Code indents a message by, and never below 60.
Only a process in the captain's pane knows that pane, while the core runs in the background and drives only its own tmux server.

## Decision

`edi sitrep` prints the boxed captain report, rendered by the core from the fleet snapshot ([fleet-snapshot](fleet-snapshot.md)); `/sitrep` is the only overview command and is read-only, and `/sitrep board` also opens the console's sitrep page ([console](console.md)).
`edi report` prints the turn report instead, which keeps only what needs the captain and draws its decision zone with the same `DecisionZone` ([turn-report](turn-report.md)).
`CaptainReportPresenter` ports the old rules one for one: the decision zone (`DecisionZone`), sections with counts, an 18-column project column with Fleet first (`ProjectColumn`), word wrapping with hanging indents that never breaks a word (`WordWrap`), BACKLOG with the five newest-filed queued tasks, and no trailing spaces.
The golden files in `edi-core/src/test/resources/captain-report/` were produced by running the old renderer on every case of its suite, and `ReportGoldenCases` builds the same state through EDI's use cases.
Three old inputs have no counterpart in EDI and are adapted openly: a hold that is not for the captain becomes a deferral until the captain's word, drawn as the old `- on hold`; a decision deferred to a date becomes a task deferred without a card on both sides; and the old `--` call, held without a card, is not ported, because EDI holds a task for a decision only with its card.
Text keeps every character the old renderer accepted, and replaces any other with `?` (`Glyphs`), so stored data can never stop a report.
The zone draws a decision bound to a listening research board as one line under that board, its number and title only, because the captain answers it on the board; every other decision keeps its full card, and the zone's count counts each decision, review, changing request and board with questions of its own once ([fleet-snapshot](fleet-snapshot.md)).
The turn's own wording arrives as `edi report --turn -` for the turn report, which `TurnDocument` reads strictly, with only `done`, `next` and `tasks`; it can reword a task but never add or remove a decision.
The command measures its own pane: `TmuxPane` asks tmux, naming the socket from `TMUX` with `-S` and waiting at most two seconds, and sends `paneWidth`; `ReportWidth.forPane` applies the rule, and without a width the report is 120 columns.
The report heads itself with the presence when it is not plainly present, with an unreadable calendar's line while one lasts ([presence](presence.md)), and with a `Builds:` line while several workers run native builds or test runs at once ([local-native-builds](local-native-builds.md)).

## Why

Golden files written by the old renderer test what it really did, and byte equality shows that nothing in the approved design drifted.
The pane is a property of the caller's terminal, so the caller measures it, and the core never runs a program against the captain's own tmux server.

## Alternatives rejected

- Porting the old assertions as fragments - every byte they did not mention could drift unseen.
- Refusing unknown characters, as the old renderer did - one emoji in a stored title would block every report.
- The core asking tmux for the caller's pane, or a `--width` option - the core would run tmux on a server that is not EDI's, or the primary would measure its pane every turn.

## Consequences

- Good: any change to the layout fails a golden file.
- Bad: the golden files cannot be produced again once the old harness is archived, so a deliberate change edits them by hand; `parked-calls` and `wrapping` already differ, because a blocked task's decision now shows in the zone.

## When to revisit

When the captain asks for a change to the report's layout, or the report is read somewhere other than a tmux pane.

## How we got here

- 15/10/2026: the captain chose `/sitrep` as the only overview command; slice S2 ported the renderer with golden files and had the command measure its pane.
- 21/10/2026: everything waiting on the captain joined the decision zone, which changed two golden files on purpose.
- 26/10/2026: a scout's board decisions showed their full cards beside the board, so a board-bound decision became one line under its board (EDI-51).
- 26/10/2026: a request still changing after its done joined the zone as "Changing, hold the merge" with why, counted like a review (EDI-53).
- 27/10/2026: the report began heading itself with the heavy runs that several workers run at once (EDI-99).
- 28/10/2026: `edi report` became the turn report, the captain's attention list, and this layout stayed the sitrep's (EDI-133).
