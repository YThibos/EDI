---
name: quiet
description: >-
  Owns the captain's /quiet and /quiet off.
  Load it when the captain types /quiet or /quiet off, or asks in their own words for quiet or for its end.
user-invocable: true
---

# quiet

## Plain `/quiet`

1. Run `edi presence quiet`.
2. Answer with one plain line to the captain that names the new presence in plain words.
3. From then on, a turn that only handles wakes ends with no text at all; `edi-presence` says what quiet means.

## `/quiet off`

1. Run `edi presence present`.
2. End the turn as a present turn ends, naming the new presence in its plain line.

## The argument

- Only the literal word `off` ends quiet.
- Any other argument is named as unknown in one line, nothing is set, and the line names the presence `edi presence` shows, since the typed message itself ends quiet or away.
