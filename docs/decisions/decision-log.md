# The decision log keeps one current record per architecture or design decision, named by its topic

**Kind:** Design

## Context

EDI's code carries no explanatory comments, so the why of every architecture and design decision needs one findable place.
The log grew to about 170 sequentially numbered records in four days, with a hand-maintained index in `docs/decisions/README.md`.
Every pull request that added a record took "the next number" and edited the index, so parallel pull requests always conflicted, and renumbering on merge broke references.
Records were append-only, so a decision changed three times was read across three or four records, each restating the last.
Many records held the captain's answers to questions, preferences and operating rules rather than decisions about how EDI is built.

## Decision

`docs/decisions/` holds one record per architecture or design decision, in a file named by a stable topic slug, such as `store.md` or `publish-guard.md`, with no number.
A record is the current state of its decision: Context, Decision, Why, Alternatives rejected, Consequences, When to revisit, and How we got here, a short dated history of how the decision came to be what it is.
A change to a decision rewrites its record in place and adds a line to How we got here; a new decision is a new file; a decision that no longer holds anywhere is deleted, and git keeps its history.
A record is warranted only for an architecture decision, which reshapes boundaries, technology or an integration, or a design decision, which changes how a component behaves and had real alternatives.
The captain's answers, preferences and "keep this in mind" rules are not records: they live in EDI's memory, the primary's anchor, a skill, `AGENTS.md` or a brief.
`README.md` holds only these rules and the template; nothing lists the records by hand, and the directory listing with each record's title is the index.
Code carries no explanatory comments; a record names the classes, files and settings it governs, so a search for any of those names finds the why, and anything that cites a record names its file.
`DecisionLogTest` fails the build on a record whose name is not a topic slug, that lacks its title, kind or a required section, on a link to a record that does not exist, and on any sequence-numbered record reference; its repository scans leave out the verbatim captured test fixtures under `src/test/resources`.
Worker briefs and the `edi-decision-log` skill say when a record is warranted and how to write one ([worker-brief](worker-brief.md)).

## Why

Two pull requests that record different decisions touch different files and never conflict; two that change the same decision conflict on that one record, which is a real conflict to settle.
A newcomer reads one record per decision instead of following a chain of supersessions, and How we got here keeps the path EDI took without keeping every step as its own document.
A log of decisions only stays short enough to read in one sitting, while preferences and operating rules belong where agents load them.

## Alternatives rejected

- Numbers drawn from a counter or a date - still one shared sequence or a long, meaningless name, and an index to keep beside it.
- A generated index committed by a build step - still one shared file every pull request rewrites.
- Append-only records with supersession - correct history, but a reader must assemble the current decision from several records, and the history is already in git.
- Recording every answer and rule the captain gives - the log became a transcript rather than an account of how EDI is built.

## Consequences

- Good: adding a record never conflicts, and the log reads as EDI's design rather than its correspondence.
- Bad: a record's earlier wording is only in git, and a changed decision needs its How we got here line written by hand.
- Bad: a topic slug must be chosen with care, because renaming it means updating every file that cites it, which the test enforces.

## When to revisit

When two records keep changing together, which says they are one decision, or the log grows past what a newcomer reads in one sitting.

## How we got here

- 15/10/2026: slice S0 started the log as numbered, append-only records with an index, and made it the only home of the why, since code carries no comments.
- 23/10/2026: parallel pull requests conflicting on every record made the captain ask for a log of decisions only, compacted and conflict-free; about 170 records became one record per decision, named by topic, and the captain's answers and rules left the log (EDI-42).
