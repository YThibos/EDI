# The core holds the captain's presence, turns quiet on silence or a meeting but never away, and briefs the captain on return

**Kind:** Design

## Context

During meetings the primary's chat kept printing, and important messages scrolled out of sight; the captain forgot to switch modes by hand.
The captain also presents the agentic flow in meetings, and must be able to pull EDI out of quiet for a demo easily.
Away changes what EDI may do on its own, so silence must never be taken as permission.
A decision the primary raises wakes nobody ([wakes-and-turn-end](wakes-and-turn-end.md)), so notifications cannot hang on wakes.

## Decision

One `presence` row holds the mode (present, quiet or away), since when, its cause (captain, silence or meeting), away's mandate and worker cap, and a demo's end (`Presence`).
`ChangePresence` sets it for `edi presence present|quiet|away --mandate|demo [off]`, the `quiet`, `present`, `afk` and `demo` skills and the console's buttons, and returns it to present at the captain's typed prompt.
Away's mandate must be words the captain typed in the exchange ([captains-word](captains-word.md)), and its worker cap is the number of workers running at that moment; `AwayLimit` holds dispatches at the cap.
`FollowThePresence` runs every supervision round: it ends a demo whose time is up, and turns present to quiet for a meeting in progress that began after the captain last typed, or after `presence.autoQuietAfter` (15 minutes) without a typed prompt; nothing but the captain's words sets away.
`MeetingCalendar` counts an entry as a meeting when it is timed, under four hours, shown busy, accepted, organised or the captain's own, and not matching `presence.calendarIgnore`; the calendar comes from the Windows side ([windows-side](windows-side.md)).
A demo holds present until `/demo off`, the end of the meeting in progress, or three hours.
In quiet and away, routine wake turns end without chat text and are batched every `presence.quietBatch` (30 minutes), except a worker's question, block or done, which the primary settles alone and gets at once ([wakes-and-turn-end](wakes-and-turn-end.md)); `NotifyTheCaptain`, a follower of the event log ([events-and-followers](events-and-followers.md)), raises one notification for each event since quiet began that concerns the captain, such as a decision, a draft for review, a failed or blocked status, a death or a failed delivery (`CaptainNotices`).
When the captain's typed prompt ends quiet or away, `PrimaryHooks` answers it with a return brief built by `ReturnBriefQuery` from the snapshot and the event log since presence left present: decisions waiting, landed work, setbacks and running work; when the console ended it, `unbriefed_absence` keeps the brief for the next typed prompt.
The console shows the presence with Present and Demo buttons, but quiet and away are set only in the chat, and the sitrep's captain report heads itself with the presence when it is not plainly present; the turn report leaves it out ([turn-report](turn-report.md)).

## Why

Important items arrive at the bottom of the chat where the captain is reading, the console always shows the current state, and silence never authorises work.
The rules are domain transitions tested with a controllable clock, and one row read by every surface keeps them in agreement.
The event log holds every captain-relevant fact once, with its time, so each notifies exactly once even across a restart.

## Alternatives rejected

- Manual modes only - the spam happened because modes were forgotten.
- Automatic away - would let silence authorise work.
- Quiet for every meeting entry in progress - would turn quiet again while the captain types during a meeting.
- Notifying from wakes or from the primary's turns - decisions the primary raises create no wake, and a quiet primary says nothing.
- Every mode on the console - away needs a mandate typed in the chat, where it can be verified.

## Consequences

- Good: AT-46 holds, meetings no longer bury decisions in scrollback, and a burst of events in quiet becomes one turn.
- Bad: presence lags a meeting's start by up to a round, and a status the primary would have settled also notifies when the worker calls it failed or blocked.

## When to revisit

When the thresholds prove wrong in daily use, the notifications prove too many, or another calendar source is needed.

## How we got here

- 15/10/2026: the captain asked for automatic away or quiet after a while without an answer, and for an easy demo mode; the specification kept away and quiet from the old harness and made quiet, never away, automatic.
- 17/10/2026: slice S8 built presence in the core, notifications from an event follower, the return brief and the console's buttons.
- 22/10/2026: the presence commands became skills of their own names ([primary-anchor-and-skills](primary-anchor-and-skills.md)).
- 28/10/2026: the turn report stopped showing the presence, which stays in the sitrep (EDI-133).
- 03/11/2026: the quiet batch held the pipeline gates and worker questions the primary settles alone for up to 30 minutes, so those wakes reach it at once in quiet and away, and only what speaks to the captain keeps the batch (EDI-55).
