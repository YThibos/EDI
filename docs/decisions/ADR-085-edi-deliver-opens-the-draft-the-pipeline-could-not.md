# ADR-085: `edi deliver` opens the draft the pipeline could not, behind the same guards

**Date:** 16/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** When the pipeline cannot open the draft itself, as on a project whose pipeline ran under the wrong account, the worker pushes the validated head and `edi deliver <id>` opens the draft pull request with the bound account from the recorded branch, with the same guards: draft only, the title carries the key, the body is plain, no agent footer (section 13.3, backlog `fo-ship-it-command`).
AT-38 and AT-43 refuse a leaking branch and a changed origin at `edi deliver` as at the worker's push.

**Decision:** `DeliverDraft` takes a ship delivered `draft-review` on a project that registers a forge, and its isolated copy.
It first runs the push checks of `PublishCheck.checkPush` on `origin/<base>..HEAD` against the copy's push URL: the publication target (ADR-086), the workflow scope (ADR-080) and the publish guard (ADR-032); any finding refuses the delivery, named by place and rule, never by value.
It then records the open merge request from the branch when one exists, or opens one through the forge as a `DraftProposal`: the title is the work key and the task's title, the body is the task's `report.md` or its title, and a proposal whose body carries a `Co-authored-by` or "Generated with" line is refused.
The merge request must then hold every delivery claim of ADR-083, so a non-draft or a head the remote does not hold is refused, and the delivery is recorded as `DraftOpened` when EDI opened it, `DraftRecorded` when it found it.
Only the primary runs it.

**Motivation:** The one path where EDI itself opens a merge request carries every guard the worker's own push carries, so it can never publish more than the pipeline would have.

**Alternatives considered:**
- Pushing the branch from `edi deliver` too - the worker's push already passes the hooks, and EDI keeps no push capability of its own.
- Opening a ready pull request when the forge cannot make drafts - I-04 allows drafts only.

**Consequences:**
- Good: the delivery a worker could not finish is one primary command, with the same evidence in the task's timeline.
- Bad: a draft opened from a head the remote does not yet hold stays open, unrecorded, until the worker pushes and `edi deliver` runs again.

**Revisit when:** The pipeline opens drafts under the bound account on every project.

**Sources:** EDI rewrite specification, section 13.3 and I-04, I-38, I-39 (AT-38, AT-43); ADR-032, ADR-080, ADR-083, ADR-086; `application.delivery.DeliverDraft`, `domain.delivery.DraftProposal`, `AgentAttribution`, `web.delivery.DeliveryController`, `edi-cli` `DeliverCommand`.
