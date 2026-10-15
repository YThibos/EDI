# ADR-044: The migration reads the old backlog with its own grammar and maps it through the domain

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** `edi migrate firstofficer --from <home> --dry-run` must print the plan and its counts, change nothing, and report every item it cannot map (section 18, AT-41).
The old harness keeps its backlog in Markdown whose grammar its backlog tool, tasks-axi, defines: three sections, a bullet per task, indented body lines, and canonical tags read only from the end of a line, such as `(repo: X)`, `(since D)`, `(hold: R)` with base64-encoded reasons, and `blocked-by: id - reason`.
Its decisions live in task bodies as `Captain decision id: Dnn` and `Captain decision card: {...}` lines, and an answered decision keeps a `Captain decision:` block with the captain's words.

**Decision:** `adapter.fork` reads the old home exactly as tasks-axi does: `BacklogLines` walks the sections and bullets, `TailTags` strips the trailing tags right to left, keeping the rightmost of a repeated tag and leaving mid-sentence parentheses in the title, and `HoldReason` decodes encoded hold reasons; tests replay tasks-axi's cases, and the real backlog was checked against tasks-axi's own counts.
`ForkMigrationPlanner` then maps every entry through EDI's domain constructors, so the plan holds the tasks and decisions the import would write, and an entry the domain refuses becomes a note for the captain:
- kinds `task` and `docs`, and an entry without a kind, become ships; any other kind is not imported;
- a task's projects are its decision card's projects, else its `repo` tag; `repo: -` names no project and is listed;
- a captain hold becomes a deferral until its `hold-until` date or until the captain's word, or the hold of its open decision; other hold kinds are listed and not carried;
- a decision is open while its task is not done and still carries a captain hold; otherwise it is answered, with the whole recorded block as its verbatim answer, the option named first on its `Answer:` line, that line as its interpretation, and the board channel when the block names a captured board result; a settled decision without a recorded answer is listed;
- a `blocked-by` edge to a task outside the backlog is dropped and listed; other links are not carried;
- the decision sequence is the highest D number in `data/decision-seq`, the backlog and both archives, and the plan names the file it came from; the EDI work-key sequence is the highest `EDI-n` in the titles;
- entries of other sections, such as a resolved "Captain's Call", are set aside and counted, and stray lines are listed.
Slice S1 plans the import of the store only; the archives, the memory and the configuration rows of section 18 join the plan in the slices that own those files, and the import itself runs at the cutover.

**Motivation:** Counts that match the old harness's own reading are the only convincing proof, and a plan made of real domain objects cannot promise an import the store would refuse.

**Alternatives considered:**
- Calling tasks-axi from the core - a Node tool in EDI's runtime (ADR-014) and a dependency on the old harness at run time.
- A looser line parser - it would disagree with tasks-axi on the edge cases the old backlog actually contains.

**Consequences:**
- Good: the dry run on a copy of the old home matched tasks-axi's counts per state and its holds, and listed every stale dependency and projectless task.
- Bad: EDI owns a port of a grammar it will stop needing after the cutover.

**Revisit when:** The cutover is done and the old home is archived; the reader can then be removed.

**Sources:** EDI rewrite specification, sections 8.3, 18 and AT-41; tasks-axi 0.2.6 `backends/markdown-grammar.js`; `adapter.fork`, `application.migration`, `invariant.BeyondTheInvariantsTest`, `edi-core/src/test/resources/fork-home`.
