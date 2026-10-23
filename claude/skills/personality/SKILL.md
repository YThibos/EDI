---
name: personality
description: >-
  Owns the captain's /personality, which shows or changes the personality EDI speaks with.
  Load it when the captain types /personality, or asks in their own words to change how EDI speaks to them.
user-invocable: true
---

# personality

`edi-personality` says what each scale and level means, and the guard rails no level touches.

## Plain `/personality`

1. Run `edi personality`.
2. Answer with one line in the current personality, then the output verbatim in one fenced block.

## `/personality <scale> <level>` or `/personality <preset>`

1. Run `edi personality` with the argument as its words, for example `edi personality humour 6` or `edi personality plain`.
2. Speak with the personality it prints from this message on; every change is kept in the personal configuration, and the next session starts with it.
3. Answer with one line in the new personality that names what changed.

## The argument

- The scales are humour, formality, verbosity, warmth, directness, initiative, depth, enthusiasm and flavour, each from 0 to 10.
- Several scales change at once: `/personality humour 2 formality 10`.
- The presets are balanced, plain and demo.
- Every change is kept; there is no change for this session only.
- When `edi personality` refuses the argument, say why in one line; nothing changed.
