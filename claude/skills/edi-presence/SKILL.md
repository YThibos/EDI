---
name: edi-presence
description: >-
  Owns what the captain's presence means: present, quiet and away, the away mandate, the demo hold and the return brief.
  Load it when presence changes on its own, or when the captain asks what a presence means.
user-invocable: true
---

# edi-presence

`edi presence` shows the current presence; the console shows it too, with Present and Demo buttons.

## The captain's commands

Each is a skill of its own name: `/quiet` and `/quiet off`, `/present`, `/afk <words>`, `/demo` and `/demo off`.

## What each mode means

- Present: every wake that concerns the captain is handled and reported in chat.
- Quiet: wakes are handled with no chat text at all, gathered into one turn at a time, and every decision, failure, blocker and ready draft raises one Windows notification instead.
- Away: as quiet, and you may carry on with work inside the captain's mandate, never beyond it.
- Away caps the workers at the number running when the captain left, so a dispatch beyond it waits for the captain's return.
- Away never widens EDI's authority: destructive, irreversible and security-sensitive acts still need the captain's word typed in the session.

## Automatic changes

- No typed prompt for 15 minutes switches present to quiet.
- A real meeting in progress switches present to quiet where the calendar is configured: timed, under four hours, busy, accepted or organised by the captain.
- All-day, long, free, tentative and out-of-office entries never count, nor do the presence markers the captain's settings list.
- Silence never switches to away.
- A demo holds present through automatic and calendar quiet until `/demo off`, the end of the current meeting or three hours.
- A demo speaks with the demo personality; once it ends on its own, run `edi personality` and speak with the captain's own again.

## Coming back

- The captain's next typed message ends quiet or away.
- That message arrives with an EDI return brief: show it verbatim in one fenced block first, then answer the message.
- A turn that handles wakes while the captain is quiet or away ends with no text at all.
