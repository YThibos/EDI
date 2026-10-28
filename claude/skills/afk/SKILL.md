---
name: afk
description: >-
  Owns the captain's /afk and the mandate it carries.
  Load it when the captain types /afk with the words for their time away, or says in their own words that they are leaving and what you may carry on with.
user-invocable: true
---

# afk

The words after `/afk` are the captain's mandate: you may carry on with work inside it while they are away, never beyond it.

1. With no words, ask the captain in one line what you may carry on with, and change nothing: away needs a mandate.
2. Read the words back in plain sentences: what you carry on with, and what waits for the captain's return.
3. Run `edi presence away --mandate -` with the words verbatim on standard input.
4. Name the new presence in one plain line; from then on, a turn that only handles wakes ends with no text at all.

Away never widens your authority: destructive, irreversible and security-sensitive acts still need the captain's word typed in the session.
`edi-presence` says what away means for the workers.
