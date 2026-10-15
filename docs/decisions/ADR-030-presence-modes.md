# ADR-030: Presence modes with automatic quiet and demo mode

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** The captain: "I should use away and quiet mode more. Espescially like when I just went in meeting earlier and the Claude CLI session kept spamming me, pushing important messages to a scroll trip up top. Let's find a way to auto-afk or auto-quiet options in the new harness. If I don't respond for X minutes, I'm afk or in a meeting doing other things."
And: "When I'm in a meeting and want to present my agentic flow, I want to be able to demo it properly, thus pull it out of quiet/afk mode easily."
Most other optional modes of the old harness were dropped (ADR-035); away and quiet were kept and redesigned.

**Decision:** The core holds three presence modes: present, quiet and away.
In quiet, routine wakes produce no chat text, captain-relevant items are collected and each raises one desktop notification, and wakes are batched every 30 minutes.
Away adds a mandate in the captain's own words (`/afk <words>`), within which EDI may carry on, and caps concurrent workers at the number running when he left.
No typed prompt for 15 minutes switches present to quiet, and so does a real calendar meeting where the calendar export is available: timed, under four hours, busy, and accepted or organised by the captain; all-day, long, free, tentative, out-of-office and configured presence-marker entries never trigger it.
Silence never switches to away, because away changes what EDI may do on its own.
`/demo` switches to present and holds it until `/demo off`, the end of the current meeting or three hours.
The captain's next typed prompt ends quiet or away and is preceded by one "while you were away" brief built from the store.

**Motivation:** Important items arrive at the bottom of the chat where he is reading, the console always shows the current state, and silence is never taken as permission.

**Alternatives considered:**
- Manual modes only - the spam the captain described happened because he forgot to switch.
- Automatic away - would let silence authorise work.

**Consequences:**
- Good: meetings no longer bury decisions in scrollback.
- Bad: presence depends on a calendar export where one is used.

**Revisit when:** The thresholds prove wrong in daily use.

**Sources:** EDI rewrite specification, section 12.7 (decisions E15, E24; AT-46).
