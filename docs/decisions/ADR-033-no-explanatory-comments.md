# ADR-033: No explanatory comments in code

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The captain's rule for his projects: code carries no explanatory comments, and the decision log carries the why.
Comments that restate code drift from it, and agents reading code later need the why in one findable place.

**Decision:** EDI's code carries no explanatory comments.
Only licence headers, suppression justifications, and TODO or FIXME lines that carry an EDI work key are allowed.
Names carry the meaning, and the decision records in `docs/decisions/` carry the why and name the classes, files and settings they govern.
Below version 1.0.0 nothing legacy lingers: no deprecation shims and no "previously" prose.

**Motivation:** One source for every why, found by searching for the name of the thing it governs.

**Alternatives considered:**
- Explanatory comments where helpful - they drift, and they scatter the why.

**Consequences:**
- Good: code reads as code; the history of a decision is in its record.
- Bad: a reader must look up a record for the why.

**Revisit when:** Never for the rule.

**Sources:** EDI rewrite specification, section 6; `.no-mistakes.yaml` (`review.path_instructions`).
