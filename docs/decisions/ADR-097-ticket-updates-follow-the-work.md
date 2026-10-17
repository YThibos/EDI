# ADR-097: Ticket updates follow the work: the sprint at the first dispatch, the draft's link, and the merged status after the merge

**Date:** 17/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** On a JIRA project a ticket is added to the current sprint when work starts on it, every shipped ticket moves to the after-merge status by walking intermediate transitions, evergreen tickets get worklogs only, and a story stays open until the captain closes it (section 13.6); `link(key, url)` ties a draft to its ticket (section 14.1).

**Decision:** `FollowTickets` follows the event log (ADR-096) for tasks keyed on a ticket of an external tracker that is not evergreen:
- the task's first `TaskDispatched` adds the ticket to the one active sprint of the project's scrum boards; a ticket already in a sprint is left there, no active sprint leaves it as it is, and several active sprints are refused rather than guessed;
- `DraftRecorded` or `DraftOpened` links the draft to the ticket under the draft's address as the link's global id, so the same link is never added twice;
- `DraftMerged`, seen by EDI or reported by the captain, walks the ticket to the machine profile's `mergedStatus` along its `workflow`, taking each time the available transition that goes furthest without passing it; a ticket already there or beyond is left alone, and EDI never moves a ticket to a later status such as done.
Each update the tracker refuses is recorded as `TicketUpdateRefused` and wakes the primary; an unreachable tracker stops the follower until its next round.

**Motivation:** The tracker shows the work's real state without the captain or a worker touching it, and no step can close a story or move an evergreen ticket.

**Alternatives considered:**
- Transitioning in `FollowMerges` itself - a slow tracker would hold up the clone refresh and the clean-up.
- Moving tickets for work delivered to the captain's checkout - EDI sees that work landed in his copy, not merged, so it does not claim a merge.

**Consequences:**
- Good: the timeline of a task shows each ticket update next to the event that caused it.
- Bad: work delivered without a draft has no merge for EDI to follow, so its ticket is moved by hand.

**Revisit when:** The captain wants tickets moved at other moments, such as when a draft opens.

**Sources:** EDI rewrite specification, sections 13.1, 13.6 and 14.1; ADR-087, ADR-093, ADR-096; `application.tracker.FollowTickets`, `adapter.tracker.JiraTracker`, `domain.tracker.Workflow`, `WorkflowStep`, `TicketMove`, `SprintPlacement`, `domain.wake.WakeRule`.
