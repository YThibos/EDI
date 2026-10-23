# Decision records

This directory explains how and why EDI became what it is: one record for each architecture or design decision, with its motivation and the path that led to it.
Code carries no explanatory comments; a record names the classes, files and settings it governs, so a search for any of those names finds the why.
Nothing lists the records by hand: the directory listing is the index, and each record's first line is its title.
Why the log works this way is itself a record, [decision-log](decision-log.md).

## What warrants a record

1. An architecture decision: it sets or changes a boundary, a technology, a module, a port or an integration with an external tool.
2. A design decision: it sets or changes how a component behaves, and had real alternatives that were weighed and rejected.
3. Nothing else: the captain's answers to questions, preferences and "keep this in mind" rules belong in EDI's memory, the primary's anchor, a skill, `AGENTS.md` or a brief, and a fix that follows from an existing decision belongs in its commit.

## How to add or change one

1. Look for the record whose topic covers the decision first; most changes extend a decision already recorded.
2. A change to a recorded decision rewrites that record in place, so it states the decision as it stands, and adds one dated line to its How we got here.
3. A new decision is a new file named by a short, stable topic slug in lower case with hyphens, such as `publish-guard.md`: it names the topic, not the outcome, and carries no number.
4. A decision that no longer holds anywhere is deleted, and the records that cited it are updated; git keeps its history.
5. Cite a record by its file: `[publish-guard](publish-guard.md)` from another record, `docs/decisions/publish-guard.md` from anywhere else.
6. Describe the harness, never a particular person, employer or machine ([machine-and-person-specifics](machine-and-person-specifics.md)).
7. Where the source gives no motivation, say so instead of inventing one.

`DecisionLogTest` checks every record: a topic-slug file name, the title, the kind, every section below in order, and that every link to a record resolves.

## Template

```markdown
# The decision, stated in one sentence

**Kind:** Architecture | Design

## Context

What was true and what forced a decision.

## Decision

What EDI does, naming the classes, files and settings that carry it.

## Why

Why this option was chosen.

## Alternatives rejected

- An alternative - why it lost.

## Consequences

- Good: what this makes easier or safer.
- Bad: what this makes harder, and what it costs.

## When to revisit

The change in circumstances that should reopen this decision.

## How we got here

- DD/MM/YYYY: what happened and what was decided, with the work key when there is one.
```
