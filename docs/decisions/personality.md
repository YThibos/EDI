# EDI speaks with a personality of nine scales the captain sets, and formality alone governs the address word

**Kind:** Design

## Context

The voice had one adjustable part, the address word, and a rule to use it at least once in every message.
That rule set a floor and no ceiling or placement, so the primary opened every message with the address word, which the captain found too formal and repetitive.
The captain asked for TARS-style 0-10 scales and ruled out honesty as one: "You need to be honest 100% all the time, just as I am with you."
The harness stays person-agnostic ([machine-and-person-specifics](machine-and-person-specifics.md)), the anchor stays within 10,000 bytes (I-35), the six ports stay six ([ports-and-architecture-rules](ports-and-architecture-rules.md)), and every command the captain types is a skill of its name ([primary-anchor-and-skills](primary-anchor-and-skills.md)).

## Decision

EDI speaks with a personality of nine scales from 0 to 10: humour, formality, verbosity, warmth, directness, initiative, depth, enthusiasm and flavour.
The `edi-personality:` section of `config/personal.yaml` holds them, with a `demo:` override that applies on top while a demo holds ([presence](presence.md)); `PersonalConfiguration` reads it through `PersonalitySection` into `CaptainsPersonality`, matches scale names case-insensitively, and refuses a level outside 0-10, a scale it does not know or a scale written twice.
A scale the section leaves out takes its Balanced level, and a missing `demo:` override is formality 7, humour 3, verbosity 5 and flavour 7.
Without the section EDI speaks Balanced (humour 3, formality 5, verbosity 5, warmth 4, directness 6, initiative 5, depth 5, enthusiasm 3, flavour 5) and offers the Balanced, Plain and Demo presets (`Preset`) in the session, each with one sample message.
Formality alone governs the address word (`Addressing`): the chance of using it in a message is `1 - (1 - 0.6 × formality/10)^weight`, with the weights bad news 4, a decision needed 3, the session's first message 3, good news 1, a status report 0.7 and nothing to report 0.5 (`MessageContext`), told as never, rarely, sometimes, usually or almost always (`Likelihood`); it never reaches every statement, and formality 0 never uses it.
At formality 8-10 the word opens the message or a later sentence; at 0-7 it may also be a closing tag, preferred at 0-3 (`AddressPlacement`); EDI uses the address word only, never the captain's name.
Bad news runs on a fixed rail, not a setting: `Personality.forBadNews()` sets humour and enthusiasm to 0, flavour to at most 3, directness to at least 7 and verbosity to at least 3.
The session-start digest ([primary-startup-context](primary-startup-context.md)) and `edi personality` print the personality line, the address line and the bad-news line from these domain rules (`PersonalityText`), so the primary has its character from session start.
`/personality` is the `personality` skill, which runs `edi personality` with a preset or scales and levels (`PersonalityCommand`, `ChangePersonality`, `PersonalityController`); the core is the one writer of the section, rewrites only it and keeps the rest of the file as written, and every change is kept: there is no change for one session only.
The console's sitrep page offers the same change: the header's Personality latch opens `PersonalityDesk`, the Balanced, Plain and Demo presets above nine sliders from 0 to 10, which reads `GET /api/personality` as a `PersonalityView` (the address word, whether a personality is chosen, whether a demo holds, the captain's own levels and the presets) and keeps the levels through `ChangePersonality`, the path `edi personality` takes; `PersonalityController` admits the captain's console session there as it admits the primary, and serves the same path as text to the command.
Beside the sliders a sample message (`sample-message.ts`) tells the EDI-25 board's invented good or bad news at the chosen levels, restating the bad-news rail and the address-word curve for the sample only, and names each level the rail moves.
The `edi-personality` skill beside `edi-voice` holds what each level means, the guard rails and the presets, and `edi-voice` keeps the register and the translation of internal labels ([captain-facing-voice](captain-facing-voice.md)).
The anchor's Character section names the scales and the honesty no scale touches, and its voice rules defer to the personality.
The captain report, boards, decision cards, worker statuses and everything others read carry no personality, which `ArchitectureRulesTest` holds; notifications stay plain, and the console greeting keeps the address word.

## Why

The captain chooses how EDI sounds without a code change, and the address word appears where the context weighs instead of at the start of every message.
Keeping the rules for the address word and the bad-news rail in the domain makes them tested rules the digest states, rather than prose the model may drift from.
The repository keeps the meaning of each level and no captain's numbers, which live in the EDI home.

## Alternatives rejected

- An address-frequency scale beside formality - the captain asked to collapse them, since where the word goes, how often and the context weight are tied.
- A curve that reaches every statement at formality 10 - it would bring back the repetition the captain named.
- A `/personality ... for now` change that lasts one session - the captain chose to keep every change.
- Bad news as a setting - the captain chose a fixed rail.
- The personality in the sitrep document - `ArchitectureRulesTest` keeps the personality packages out of everything others read, the sitrep among them, and the desk needs the levels only while it is open.
- The sample message composed in the core - the sliders would wait on a request at every step, for a sample that carries no rule of its own.
- An output style, or a `CLAUDE.md` in the EDI home - the captain ruled both out; the anchor and the skills are the primary's only instructions.
- A new port for the personal configuration - application services already read `PersonalConfiguration`, so the six ports stay six.

## Consequences

- Good: the address word no longer opens every message, and the captain tunes humour, formality and the rest in one command.
- Good: honesty, the bad-news rail and the plain artefacts hold at every level.
- Bad: nine scales are a lot to tune; the presets and the samples in `edi-personality` carry a new captain until they choose.
- Bad: the personality is per machine, like the rest of the EDI home, so a captain with two machines sets it on each.
- Bad: the console's sample restates the bad-news rail and the address-word curve, so a change to either in `Personality` or `Addressing` is made in `sample-message.ts` too.

## When to revisit

When the captain asks to collapse scales, a new preset ships, or the anchor nears its budget.

## How we got here

- 23/10/2026: scout EDI-25 settled the scales, the address-word curve and the bad-news rail with the captain over three board rounds, and EDI-31 replaced the fixed voice with them.
- 27/10/2026: the console's sitrep page gained the personality desk, modelled on the EDI-25 board's mixing desk (EDI-98).
