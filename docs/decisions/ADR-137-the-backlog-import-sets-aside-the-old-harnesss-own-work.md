# ADR-137: The backlog import sets aside the old harness's own work, reads EDI keys from every line, and names each wait in plain words

**Date:** 19/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-044 reads the old backlog with tasks-axi's grammar and maps each entry through EDI's domain.
The dry run on the captain's home (session prep, step 4) found three surprises.
The next EDI key would be issued twice, because a task named its key `EDI-14` only in its notes and its decision card, and the planner read keys from titles alone.
Seventeen tasks on the old harness's own `firstofficer` project would be imported, although section 18 drops that project from the registry, so they would point at a project EDI does not know.
A dated wait without a hold kind was dropped with the words "a hold of kind null", an internal label in captain-facing text against AT-22's intent.

**Decision:** This record supersedes ADR-044 and keeps its grammar and mapping, changed in three places.
`adapter.fork` reads the old home exactly as tasks-axi does: `BacklogLines` walks the sections and bullets, `TailTags` strips the trailing tags right to left, keeping the rightmost of a repeated tag and leaving mid-sentence parentheses in the title, and `HoldReason` decodes encoded hold reasons.
`ForkMigrationPlanner` maps every entry through EDI's domain constructors, and an entry the domain refuses becomes a note for the captain:
- an entry whose only project is the old harness itself (`ForkHome.HARNESS_PROJECT`) improves the harness EDI replaces, so it is set aside and listed, not imported; a dependency on it is dropped and listed, and an entry that names the harness beside other projects keeps only the others;
- kinds `task` and `docs`, and an entry without a kind, become ships; any other kind is not imported;
- a task's projects are its decision card's projects, else its `repo` tag; `repo: -` names no project and is listed;
- a captain hold becomes a deferral until its `hold-until` date or until the captain's word, or the hold of its open decision; any other hold, with a kind or without one, is a wait on something outside EDI, listed as "waits until <date> on something outside EDI ("<reason>")" and not carried, since EDI holds a task only for the captain;
- a decision is open while its task is not done and still carries a captain hold; otherwise it is answered, with the whole recorded block as its verbatim answer, the option named first on its `Answer:` line, that line as its interpretation, and the board channel when the block names a captured board result; a settled decision without a recorded answer is listed;
- a `blocked-by` edge to a task outside the backlog or not imported is dropped and listed; other links are not carried;
- the decision sequence is the highest D number in `data/decision-seq`, the backlog and both archives, and the plan names the file it came from;
- the highest `EDI-n` key is read the same way, from every line of the backlog and both archives, titles, notes and cards alike, so a key named only in a body is never issued again;
- entries of other sections, such as a resolved "Captain's Call", are set aside and counted, and stray lines are listed.

**Motivation:** Counts that match the old harness's own reading stay the proof, and seeding keys above every key the old home mentions costs at most an unused number, while issuing a used one would collide branches.

**Alternatives considered:**
- Importing the old harness's tasks on an unregistered project - each would refuse its first dispatch, and the captain would have to drop seventeen tasks by hand.
- Re-homing the harness's tasks onto EDI - most concern fixes of the old scripts that EDI never had; the few that still matter were ported into the specification's ledger.
- Seeding keys from the branches on the EDI repository's origin - needs a network call during the dry run, while every key in use is written in the backlog.
- Carrying a dated wait as a deferral - would show work waiting on an outside event as waiting on the captain.

**Consequences:**
- Good: the captain sees the set-aside work by name in the dry run and can queue any of it again with `edi task add`.
- Bad: a key merely mentioned in passing raises the seed; the gap is harmless.

**Revisit when:** The cutover is done and the old home is archived; the reader can then be removed.

**Sources:** EDI rewrite specification, sections 8.3, 18, 20.1 step 4 and AT-41; session prep report, step 4 and F8; ADR-044; tasks-axi 0.2.6 `backends/markdown-grammar.js`; `adapter.fork.ForkHome`, `adapter.fork`, `application.migration.ForkMigrationPlanner`, `ForkEntryMapping`, `invariant.BeyondTheInvariantsTest`, `edi-core/src/test/resources/fork-home`.
