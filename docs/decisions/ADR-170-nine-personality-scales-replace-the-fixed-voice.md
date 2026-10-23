# ADR-170: Nine personality scales replace the fixed voice, and formality alone governs the address word

**Date:** 23/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** This record supersedes ADR-031, whose voice had one adjustable part, the address word, and the rule to use it at least once in every message.
That rule set a floor and no ceiling or placement, so the primary opened every message with the address word, which the captain found too formal and repetitive.
The captain asked for TARS-style 0-10 scales and ruled out honesty as one: "You need to be honest 100% all the time, just as I am with you."
Scout EDI-25 settled the design with the captain over three board rounds (Q1 to Q15).
The harness stays person-agnostic (ADR-017), the anchor stays within 10,000 bytes (I-35), ADR-007's six ports stay six, and every command the captain types is a skill of its name (ADR-167).

**Decision:** EDI speaks with a personality of nine scales from 0 to 10: humour, formality, verbosity, warmth, directness, initiative, depth, enthusiasm and flavour (Q1).
The `edi-personality:` section of `config/personal.yaml` holds them, with a `demo:` override that applies on top while a demo holds (Q5, Q7); `PersonalConfiguration` reads it into `CaptainsPersonality` and refuses a level outside 0-10 or a scale it does not know.
A scale the section leaves out takes its Balanced level, and a missing `demo:` override is formality 7, humour 3, verbosity 5 and flavour 7.
Without the section EDI speaks Balanced (humour 3, formality 5, verbosity 5, warmth 4, directness 6, initiative 5, depth 5, enthusiasm 3, flavour 5) and offers the Balanced, Plain and Demo presets in the session, each with one sample message (Q8).
Formality alone governs the address word (Q11, Q15 B): the chance of using it in a message is `1 - (1 - 0.6 × formality/10)^weight`, with the weights bad news 4, a decision needed 3, the session's first message 3, good news 1, a status report 0.7 and nothing to report 0.5, told as never, rarely, sometimes, usually or almost always; it never reaches every statement, and formality 0 never uses it.
At formality 8-10 the word opens the message or a later sentence; at 0-7 it may also be a closing tag, preferred at 0-3 (Q12); EDI uses the address word only, never the captain's name (Q3).
Bad news runs on a fixed rail, not a setting (Q4): `Personality.forBadNews()` sets humour and enthusiasm to 0, flavour to at most 3, directness to at least 7 and verbosity to at least 3.
The session-start digest and `edi personality` print the personality line, the address line and the bad-news line from these domain rules, so the primary has its character from session start.
`/personality` is the `personality` skill (ADR-167), which runs `edi personality` with a preset or scales and levels (Q6); the core is the one writer of the section, rewrites only it and keeps the rest of the file as written, and every change is kept: there is no change for one session only (Q13 B).
The `edi-personality` skill beside `edi-voice` holds what each level means, the guard rails and the presets, and `edi-voice` keeps the register and the translation of internal labels (Q14).
The anchor gains a short Character section that names the scales and the honesty no scale touches, and its voice rules defer to the personality.
The captain report, boards, decision cards, worker statuses and everything others read carry no personality, notifications stay plain, and the console greeting keeps the address word (Q10).

**Motivation:** The captain chooses how EDI sounds without a code change, and the address word appears where the context weighs instead of at the start of every message.
Keeping the rules for the address word and the bad-news rail in the domain makes them tested rules the digest states, rather than prose the model may drift from.
The repository keeps the meaning of each level and no captain's numbers, which live in the EDI home.

**Alternatives considered:**
- An address-frequency scale beside formality - the captain asked to collapse them, since where the word goes, how often and the context weight are tied (Q11, Q15).
- A curve that reaches every statement at formality 10 - it would bring back the repetition the captain named (Q15 A).
- A `/personality ... for now` change that lasts one session - the captain chose to keep every change (Q13 B).
- Bad news as a setting - the captain chose a fixed rail (Q4).
- An output style, or a CLAUDE.md in the EDI home - the captain ruled both out; the anchor and the skills are the primary's only instructions.
- A new port for the personal configuration - application services already read `PersonalConfiguration`, so ADR-007's six ports stay six.

**Consequences:**
- Good: the address word no longer opens every message, and the captain tunes humour, formality and the rest in one command.
- Good: honesty, the bad-news rail and the plain artefacts hold at every level.
- Bad: nine scales are a lot to tune; the presets and the samples in `edi-personality` carry a new captain until they choose.
- Bad: the personality is per machine, like the rest of the EDI home, so a captain with two machines sets it on each.

**Revisit when:** The captain asks to collapse scales, a new preset ships, or the anchor nears its budget.

**Sources:** Scout EDI-25 report and board rounds 1 to 3; EDI-31; `domain.personality`, `adapter.home.PersonalConfiguration`, `adapter.home.PersonalitySection`, `application.personality`, `web.personality`, `web.primary.SessionStartDigestText`, `edi-cli` `PersonalityCommand`, `claude/primary/CLAUDE.md`, `claude/skills/personality/`, `claude/skills/edi-personality/`, `claude/skills/edi-voice/`, `examples/edi-home/config/personal.yaml`; ADR-007, ADR-017, ADR-031, ADR-047, ADR-132, ADR-167.
