# ADR-047: The captain report matches the old renderer byte for byte

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The captain confirmed the old harness's boxed "C Rail" report design, and section 15.1 requires `CaptainReportPresenter` to reproduce the old renderer, `bin/fm-captain-report.sh`, byte for byte on the same inputs, with its test cases ported as golden files (AT-42).
The old renderer refused a whole report when any text held a character outside its glyph allow-list, while section 15.1 says such a character is replaced.
Some of the old renderer's inputs have no counterpart in EDI: a call held without a decision card, which it drew as `--`, a decision deferred to a date, and a hold that is not for the captain.

**Decision:** The report keeps the old layout and proves it against the old renderer's own output:
- `CaptainReportPresenter` renders a `CaptainReport`, the snapshot of ADR-046 with the turn and the width, through the old rules ported one for one: the decision zone (`DecisionZone`), the sections with their counts, the 18-column project label column with Fleet first and then titles in case-insensitive order (`ProjectColumn`), word wrapping with hanging indents that never breaks a word, so a long link overflows the open right edge (`WordWrap`), and no trailing spaces.
- The golden files in `edi-core/src/test/resources/captain-report/` were produced by running the old renderer itself on every case of its test suite, with invented project names, plus a 2 MB backlog and a case that wraps every kind of line; `ReportGoldenCases` builds the same state through EDI's use cases, and AT-42 compares each report with its golden file exactly.
- Three old inputs have no counterpart in EDI, and each is handled openly:
  - the old case of a quiet backlog holds one task with a hold that is not for the captain, and its EDI counterpart is a deferral until the captain's word, which the report draws exactly as the old renderer drew that hold, `- on hold`;
  - the old case of parked calls deferred a decision to a date, which EDI does not allow, so the ported case defers a task without a card on both sides and the old renderer produced its golden file from that input;
  - the old `--` call, a task held for the captain without a card, is not ported, because EDI holds a task for a decision only with its card (ADR-039), and the migration turns such a hold into a deferral (ADR-044), which parks.
- Text keeps every character the old renderer accepted: printable ASCII, Latin-1 and Latin Extended-A, the general punctuation block from the hyphen to the hyphenation point, and the euro sign; a line break or a tab becomes a space and any other character a `?` (`Glyphs`).
- BACKLOG lists the five newest-filed queued tasks, comparing the day each was filed and keeping the queue order within a day, then counts the rest, as the old renderer did.
- The primary sends what its turn did as a JSON document on standard input, `edi report --turn -`; `TurnDocument` reads it as strictly as the old renderer did, with only `done`, `next` and `tasks`, one-line texts, and every unknown field refused in one sentence; a task's wording replaces its title, adds its state and replaces its projects, but never adds or removes a decision.
- `edi sitrep` prints the same report without a turn; it is read-only.

**Motivation:** Golden files written by the old renderer test what it really did, not a description of it, and byte equality shows that nothing in the approved design drifted.
Replacing a character instead of refusing the report means stored data can never stop the report that ends a state-changing turn (I-21), and no emoji can widen a line.

**Alternatives considered:**
- Porting the old test assertions as fragments - every byte they did not mention could drift unseen.
- Refusing unknown characters as before - one emoji in a stored title would block every report until someone fixed it.
- Replacing every character outside printable ASCII, as section 15.1 words it - accented names would no longer match the old renderer's output, which byte equality rules out.

**Consequences:**
- Good: any change to the layout fails a golden file.
- Bad: the golden files cannot be produced again once the old harness is archived, so a deliberate layout change edits them by hand.

**Revisit when:** The captain asks for a change to the report's layout.

**Sources:** EDI rewrite specification, sections 9.2, 15.1 and 15.2 (AT-36, AT-42, decision D-overview); ADR-024, ADR-039, ADR-043, ADR-044, ADR-046; `web.report.CaptainReportPresenter`, `DecisionZone`, `ProjectColumn`, `WordWrap`, `Glyphs`, `TurnDocument`, `invariant.ReportGoldenCases`, `edi-core/src/test/resources/captain-report/`.
