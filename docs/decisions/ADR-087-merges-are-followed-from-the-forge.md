# ADR-087: A merge is followed from the forge's word, whether EDI sees it or the captain says so

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** ADR-023 observes each recorded open draft every 10 minutes and accepts the captain's word with `edi merged <id|url>` (decision E14).
After a merge EDI refreshes the clone, verifies the merge commit (I-29), transitions tickets (slice S6), cleans up (section 11.8) and emits the merge and clean-up events; a draft seen merged or closed wakes the primary (section 12.3).

**Decision:** `DeliveryRound` asks `FollowMerges` every `edi.delivery.drafts` (PT10M by default) to view each open delivery of a task still in flight, on the account its project binds now; a delivery whose repository the registry no longer names is left alone.
Each delivery is watched on its own: one the forge cannot show, or whose repository the registry no longer names, is logged with its task and left alone while the watch goes on.
A view that shows the merge records `DraftMerged` with the commit the merge landed, and a clone refresh that waits for that commit on the merge request's target (ADR-088); a closed one records `DraftClosed`; both wake the primary, and an open one changes nothing.
`edi merged <id|url>` takes the task's delivery, or the delivery at that exact URL, and views it at once: a merge is followed the same way under the primary's name and answered in the same call, while an open or closed merge request is refused with what the forge shows.
A merge EDI already followed is answered from the recorded delivery without a second `DraftMerged` or wake, and while its task is in flight and its refresh verified, the captain's word tries the clean-up once more.
A task delivered to the captain's checkout has no forge to ask, so `edi merged` goes straight to its clean-up, whose landed-work test decides.
Once a merge's refresh is verified, the watch tries its clean-up exactly once, either at once or in a later round; a refused clean-up wakes the primary with its findings, and one a failing program stops is logged.

**Motivation:** Neither the captain's word nor a stale view can make EDI treat unmerged work as merged, because every merge EDI follows is one the forge reports.

**Alternatives considered:**
- Taking the captain's word without asking the forge - a mistyped task id would retire live work.
- Trying the clean-up in every round - a refusal would wake the primary every 10 minutes.

**Consequences:**
- Good: the whole tail of a delivery, from merge to a cleaned-up task, runs without the captain saying anything.
- Bad: up to ten minutes pass between his merge and EDI's notice when he does not say it.

**Revisit when:** The forges offer a local notification of a merge.

**Sources:** EDI rewrite specification, sections 12.1, 12.3, 13.1 and decision E14; ADR-023, ADR-088, ADR-090; `application.delivery.FollowMerges`, `DeliveryRound`, `domain.delivery.Delivery`, `domain.wake.WakeRule`, `web.delivery.DeliveryController`, `edi-cli` `MergedCommand`.
