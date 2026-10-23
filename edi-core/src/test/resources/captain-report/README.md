# Captain report golden files

Each file is the exact output of the old harness's renderer, `bin/fm-captain-report.sh` at its commit `b8900c3b`, on the input of one case of its test suite, `tests/fm-captain-report.test.sh`, with invented project names.
`invariant.ReportGoldenCases` builds the same input in EDI's store, and AT-42 compares EDI's captain report with the file byte for byte.
The decision record [captain-report](../../../../../docs/decisions/captain-report.md) says which inputs had to be adapted and why.
Since everything waiting on the captain joined the decision zone ([fleet-snapshot](../../../../../docs/decisions/fleet-snapshot.md)), `parked-calls` and `wrapping` also show a blocked call's card in the decision zone and count it, where the old renderer showed it only under PARKED.

| File | Old test case |
|---|---|
| `busy` | the busy report renders the chosen layout, at 80 columns |
| `cumulative-open`, `cumulative-answered` | open decisions are cumulative until answered |
| `quiet-backlog`, `quiet-ongoing`, `quiet-open-call` | the backlog shows only when nothing is open or ongoing |
| `parked-calls` | aged calls stay open and parked calls keep their numbers, with the dated call deferred without a card |
| `retired-number` | a retired number stays out of parked |
| `project-groups` | items group under project titles |
| `width-default`, `width-60`, `pane-90`, `pane-40`, `pane-318` | the width is fixed and narrows to a smaller pane |
| `latin-text` | accented letters and typographic punctuation are accepted |
| `large-backlog` | a fleet snapshot past the argument limit still renders |
| `two-megabyte-backlog` | the 2 MB backlog of AT-42, 512 queued tasks with 4 KB of notes each |
| `wrapping` | added for EDI: every kind of line wraps, at 72 columns |
