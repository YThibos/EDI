# ADR-125: While the chat is quiet, each event that concerns the captain raises one notification, from an event follower

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 12.7: in quiet, captain-relevant items (decisions, failures, ready drafts, blockers, credentials) are collected, not printed, and each one raises a single Windows notification; away behaves as quiet.
AT-46: a decision in quiet produces one notification and appears in the return brief.
A decision the primary raises wakes nobody (ADR-069), so notifications cannot hang on wakes.
Followers read the event log from their own cursor and pick up after a crash (ADR-096).

**Decision:** `NotifyTheCaptain` is the follower `captain-notifications` of `EventFollowers`.
For each event, while the presence keeps the chat quiet and the event happened at or after the quiet began, `CaptainNotices` turns it into a notification when it concerns the captain: a decision raised, a draft recorded for the captain's review, a branch delivered or a change applied to the captain's local copy, a failed or blocked status, a scout's findings, a worker that died or was lost in an outage, a launch that failed, and a delivery to the captain's local copy that failed.
The notification names the task by its title and carries the decision's title or the status's own words; `WindowsToasts` shows it.
The follower's cursor makes each event notify at most once; while the captain is present nothing is notified, because the chat carries it.

**Motivation:** The event log already holds every captain-relevant fact once, with its time, so a follower notifies exactly once even across a restart, and the time check keeps old events from notifying when quiet begins.

**Alternatives considered:**
- Notifying from the wake queue - decisions the primary raises create no wake.
- Notifying from the primary's turns - a quiet primary is meant to say nothing, and its turns are batched.

**Consequences:**
- Good: the captain sees what needs them outside the terminal at the moment it happens.
- Bad: a status the primary would have settled on its own also notifies the captain when the worker calls it failed or blocked.

**Revisit when:** The notifications prove too many, or another notification channel is wanted.

**Sources:** EDI rewrite specification, section 12.7 (AT-46); ADR-069, ADR-096, ADR-122; `application.presence.NotifyTheCaptain`, `CaptainNotices`, `application.follow.EventFollowers`, `domain.presence.CaptainNotice`, `adapter.windows.WindowsToasts`, `adapter.store.EventCursorStore`.
