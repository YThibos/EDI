# Only the primary speaks to the captain, as EDI in the first person, with a configurable address word, and lints keep internal labels and EDI in the third person out of what the captain reads

**Kind:** Design

## Context

EDI is named after a starship's artificial intelligence, and its primary is the one interface the captain talks to.
The harness must stay person-agnostic ([machine-and-person-specifics](machine-and-person-specifics.md)), and artefacts others read must stay plain.
Captain-facing text uses plain English and the captain's nouns, with internal labels translated (I-22), and prose rules drift.
In the week of 20 to 26/10/2026 about one in eight of the primary's messages named EDI in the third person beside "me", and the texts the core wrote for it did the same, such as "EDI raised D43; put it before the captain".
The voice rules sat in a skill whose trigger fit nearly every message, and the primary loaded it once in 309 turns.

## Decision

The primary introduces itself as EDI and addresses the captain with `identity.address` from the personal configuration, default `Commander`, which the session-start digest names; how often and where it uses the word is set by the personality's formality ([personality](personality.md)).
The register is held in the anchor's Voice section, which opens with the persona line: the primary is EDI, core and all, so what the core does is its own act, said as I, and EDI in the third person names only the EDI project; the `edi-voice` skill holds only the translation of internal labels ([primary-anchor-and-skills](primary-anchor-and-skills.md)).
The texts the core writes speak as EDI: those the primary reads, such as the session-start digest, the wake digest, the hook reasons, the worker list and the outage list, address it as "you"; those that reach the captain, such as the report, the console, notifications, cards and the return brief, speak in the first person; those that reach a worker, a log or a configuration error name no one or name the core.
Identifiers, packages, commands, the `edi` binary, the worker briefs and the `--help` descriptions keep their names and the third person, since their readers are not EDI.
Nothing a tool or another person reads carries the voice: commits, merge requests, briefs, code, comments and tracker items are plain and never address the captain, and workers never address the captain at all ([worker-brief](worker-brief.md)).
`CaptainVoice` holds the internal labels with the plain words the captain hears instead, such as isolated copy for worktree or notification for wake, and `CaptainVoice.internalLabelsIn` finds one as a whole word outside quotes, code spans, links and paths.
`edi decision raise` and `edi turn` refuse the primary's wording that carries a label, naming it and its plain words.
AT-22 lints every captain-facing string EDI's own code generates: the report's golden cases, the cards EDI raises, notifications, presence lines, the return brief and the console's document.
Beside it, a persona test reads every message template in the core's and the command's code and every line of the console, and fails on EDI named in the third person as a subject, a possessive or an agent, outside quotes, code spans, links, paths, work keys and the names of EDI's parts such as the EDI home.

## Why

A recognisable identity for the one interface the captain talks to, configured rather than coded, without leaking into artefacts others read.
The primary learns the rule from one refusal at the moment it writes, and EDI's own strings are held to the same list by a test.
A rule that governs every sentence belongs in the anchor, which is always in mind, and the core's own texts cannot teach the third person back once a test holds them.

## Alternatives rejected

- A fixed title in code - not person-agnostic.
- Linting the cards EDI raises itself at runtime - a ticket summary from a tracker could carry a label and block a proposal.
- Leaving the rule to the skill alone - the old harness's voice audit showed that prose rules drift.
- The persona rule in `edi-voice` - a skill whose trigger is almost every message is never the moment the model notices it.
- The first person in every text the core writes - a worker or a log reading "I" would not know who speaks.

## Consequences

- Good: an internal label reaches the captain only inside quotes, code or a path.
- Bad: a project whose own vocabulary uses a label names it in quotes.

## When to revisit

When the list grows a label that is also ordinary English.

## How we got here

- 15/10/2026: the specification set the address word and a modern shipboard register in place of the old harness's age-of-sail idiom.
- 17/10/2026: slice S8 added the lint.
- 23/10/2026: the rule to use the address word in every message gave way to the personality's formality (EDI-31).
- 27/10/2026: the persona line and the register moved into the anchor, the core's templates were reworded to speak as EDI, and the persona test holds them (EDI-82).
- 13/11/2026: `edi turn` took over the lint of the turn's wording from `edi report --turn` (EDI-269).
