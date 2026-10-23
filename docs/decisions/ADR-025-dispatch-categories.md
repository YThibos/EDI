# ADR-025: Dispatch by eight written categories and a project override

**Date:** 15/10/2026

**Status:** Superseded by ADR-174

**Kind:** Design

**Context and constraints:** The old harness routed work with sixteen rules and a default, with inconsistencies found in review.
The captain: "compact them too. Maybe four simple categories: research (aka planning), implementation/development, documentation, and bugfixing. Suggest more categories if they could add value (or decrease token cost by delegating to lower models when feasible and foolproof)."
Effort has three levels and `high` is the ceiling (I-14); quality is never traded for cost (I-17).

**Decision:** `config/dispatch-rules.json` holds eight categories, each with its model, effort and a written reason:

| Category | Model, effort |
|---|---|
| `research` | Opus 5.5, high |
| `implementation` (with a settled plan) | Sonnet 5.5, high |
| `implementation-unplanned` | Opus 5.5, high |
| `bugfix` (established cause) | Sonnet 5.5, high |
| `documentation` | Sonnet 5.5, low |
| `operations` | Sonnet 5.5, medium |
| `mechanical` | Haiku 5.5, no effort flag |
| `review-fix` | Sonnet 5.5, medium |

A project override sits above every category: every task on the EDI project runs on Opus 5.5 at high effort ("Nothing more, nothing less.", I-15).
An explicit per-task instruction from the captain outranks both and is recorded as an override.
`mechanical` is foolproof by construction: the brief states the exact expected change, `done` is accepted only if the full verify profile passes and the diff touches only the named files, otherwise EDI re-dispatches the task as `implementation`, and a mechanical task never edits a decision, a migration, security configuration or a test's expected values.
The loader rejects any effort above `high`, a category without a reason, and model ids outside the machine profile's allow-list.

**Motivation:** Few categories the primary can pick by judgement, one token-saving category made safe by mechanical checks, and the EDI project kept on the captain's standing order.

**Alternatives considered:**
- Third-party automatic routing - declined by the captain: "as few third-party services as possible".
- Keeping the sixteen rules - hard to apply consistently.

**Consequences:**
- Good: a relaunch re-resolves model and effort from the category (I-16).
- Bad: the primary's choice of category is judgement, not a rule.

**Revisit when:** The research-first pilot (ADR-026) reports, or a model generation changes.

**Sources:** EDI rewrite specification, section 8.6 (decisions E13, E21; I-14 to I-17).
