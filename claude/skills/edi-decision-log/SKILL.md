---
name: edi-decision-log
description: >-
  Owns architecture and design decision records on projects that keep a decision log.
  Load it before shipping on a project that keeps one, and before briefing a worker whose change settles or changes a decision.
---

# edi-decision-log

The decision log lives in the project's repository; a worker writes its records as part of the change, never you.

## In the brief

1. Say in the spec which decisions the change settles or changes, and that the worker records each one in the project's decision log.
2. Name the project's own decision log path and its existing records to read first.
3. On a project with no explanatory comments, say that the records carry the why and name the classes they govern.

## The record's format

Each record holds, in this order:

- Decision: what was decided, in one or a few sentences.
- Context and constraints: what forced the decision.
- Motivation: why this choice.
- Alternatives considered: each with why it lost.
- Consequences: the good and the bad.
- Revisit when: the condition that reopens it.
- Sources: the specification sections, other records and the classes it governs.

## Rules

- Records are append-only: a changed decision is a new record that supersedes the old one, and the old one says so.
- One decision per record, numbered after the highest one in the log.
- United Kingdom English, a plain "-" and never another dash, one sentence per line.
- A decision the captain has not settled is not recorded; it becomes a decision for the captain first.
