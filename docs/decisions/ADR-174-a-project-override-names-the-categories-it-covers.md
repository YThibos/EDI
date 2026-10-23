# ADR-174: A project override names the categories it covers

**Date:** 23/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** This record supersedes ADR-025, whose project override sat above every category, so every task on the EDI project ran on Opus 5.5 at high effort.
The override's own reason and I-15 say otherwise: EDI's own research and implementation run on Opus 5.5 at high.
With the override unscoped, EDI's documentation and operations tasks also ran on Opus at high, while the captain had routed those categories to Sonnet 5.5 at high for every project.
The captain (23/10/2026): "Narrow the EDI override in the rule file to research and implementation, as its explanation says. make it so".
Effort has three levels and `high` is the ceiling (I-14); quality is never traded for cost (I-17).

**Decision:** `config/dispatch-rules.json` holds eight categories, each with its model, effort and a written reason, as ADR-025 set them out: `research`, `implementation` (with a settled plan), `implementation-unplanned`, `bugfix` (established cause), `documentation`, `operations`, `mechanical` and `review-fix`.
A project override may name the categories it covers in `categories`; it routes a task of the project only when the task's category is one of them, and a task of any other category routes by its category's own rule.
An override without `categories` covers every category of its project.
The EDI override covers `research`, `implementation`, `implementation-unplanned` and `bugfix` and runs them on Opus 5.5 at high effort; `examples/edi-home/config/dispatch-rules.json` says so.
`DispatchRules.validatedAgainst` refuses an override that covers a category the rules do not hold, naming the categories they do.
An explicit per-task instruction from the captain outranks both and is recorded as an override, and `mechanical` stays foolproof by construction, as ADR-025 decided.

**Motivation:** The override does what its reason says, and EDI's prose, configuration and housekeeping tasks route by the same category rules as every other project's.
Keeping an unscoped override possible leaves a whole-project override, for a project that needs one, a one-line rule.

**Alternatives considered:**
- Requiring `categories` on every override - a project that truly needs one route for all of its work would have to list every category and keep the list current as categories change.
- Excluding categories instead of naming them - the override's reason names the work it covers, so the rule names the same.
- Moving EDI's research and implementation into new EDI-only categories - the primary would have to pick a different category for the same kind of work depending on the project.

**Consequences:**
- Good: AT-15 checks that EDI's research and implementation resolve to the override and its documentation to the documentation category.
- Good: a misspelt category in an override is refused when the rules are read, never silently ignored.
- Bad: the captain's live rules file needs the `categories` line for the narrower routing to take effect; until then the override still covers every category.

**Revisit when:** A category is added, renamed or split, or a second project needs a scoped override.

**Sources:** EDI-46; EDI rewrite specification, section 8.6 and I-14 to I-17; ADR-025, which this supersedes; ADR-050; `domain.dispatch.ProjectOverride`, `domain.dispatch.DispatchRules`, `adapter.home.DispatchRulesFile`, `examples/edi-home/config/dispatch-rules.json`, `invariant.DispatchAndEffortTest`.
