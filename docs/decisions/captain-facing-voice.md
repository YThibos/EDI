# Only the primary speaks to the captain, with a configurable address word, and a lint keeps internal labels out of what the captain reads

**Kind:** Design

## Context

EDI is named after a starship's artificial intelligence, and its primary is the one interface the captain talks to.
The harness must stay person-agnostic ([machine-and-person-specifics](machine-and-person-specifics.md)), and artefacts others read must stay plain.
Captain-facing text uses plain English and the captain's nouns, with internal labels translated (I-22), and prose rules drift.

## Decision

The primary introduces itself as EDI and addresses the captain with `identity.address` from the personal configuration, default `Commander`, which the session-start digest names; the register is held in the anchor and the `edi-voice` skill ([primary-anchor-and-skills](primary-anchor-and-skills.md)).
Nothing a tool or another person reads carries the voice: commits, merge requests, briefs, code, comments and tracker items are plain and never address the captain, and workers never address the captain at all ([worker-brief](worker-brief.md)).
`CaptainVoice` holds the internal labels with the plain words the captain hears instead, such as isolated copy for worktree or notification for wake, and `CaptainVoice.internalLabelsIn` finds one as a whole word outside quotes, code spans, links and paths.
`edi decision raise` and `edi report --turn` refuse the primary's wording that carries a label, naming it and its plain words.
AT-22 lints every captain-facing string EDI's own code generates: the report's golden cases, the cards EDI raises, notifications, presence lines, the return brief and the console's document.

## Why

A recognisable identity for the one interface the captain talks to, configured rather than coded, without leaking into artefacts others read.
The primary learns the rule from one refusal at the moment it writes, and EDI's own strings are held to the same list by a test.

## Alternatives rejected

- A fixed title in code - not person-agnostic.
- Linting the cards EDI raises itself at runtime - a ticket summary from a tracker could carry a label and block a proposal.
- Leaving the rule to the skill alone - the old harness's voice audit showed that prose rules drift.

## Consequences

- Good: an internal label reaches the captain only inside quotes, code or a path.
- Bad: a project whose own vocabulary uses a label names it in quotes.

## When to revisit

When the list grows a label that is also ordinary English.

## How we got here

- 15/10/2026: the specification set the address word and a modern shipboard register in place of the old harness's age-of-sail idiom.
- 17/10/2026: slice S8 added the lint.
