---
name: edi-decision-log
description: >-
  Owns architecture and design decision records on projects that keep a decision log.
  Load it before shipping a change that settles or changes an architecture or design decision on such a project.
---

# edi-decision-log

The decision log lives in the project's repository; a worker writes or updates its record as part of the change, never you.

## What warrants a record

- An architecture decision: a boundary, a technology, a module, a port or an integration with an external tool.
- A design decision: how a component behaves, chosen over real alternatives.
- Nothing else: the captain's answers, preferences and "keep this in mind" rules go to memory, the anchor, a skill or a brief, and a fix that follows from a recorded decision needs no record.

## In the brief

1. Say in the spec which decision the change settles or changes, and that the worker records it in the project's decision log.
2. Name the log's path, the README that holds its rules, and the records to read first.
3. On a project with no explanatory comments, say that the record carries the why and names the classes it governs.

## A topic-named log, such as the EDI project's

- One record per decision, in a file named by a stable topic slug, such as `docs/decisions/publish-guard.md`, with no number and no index to edit.
- A changed decision rewrites its record in place and adds a dated line to its How we got here; a new decision is a new file.
- Each record holds Context, Decision, Why, Alternatives rejected, Consequences, When to revisit and How we got here, as the log's README template shows.
- Cite a record by its file, never by a number.

## Any other log

Follow the format and naming rules the log itself states; when it numbers its records, the worker takes the next free number and expects to renumber on a conflict.

## Rules

- United Kingdom English, a plain "-" and never another dash, one sentence per line.
- A decision the captain has not settled is not recorded; it becomes a decision for the captain first.
