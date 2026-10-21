# ADR-159: A worker's question is EDI's, and the acknowledged steer that resolves it settles it

**Date:** 21/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** A worker reports `needs-decision` or `blocked` to the primary, never to the captain (ADR-055); the primary settles it or raises a decision card for the captain.
ADR-107 showed an ongoing task's state as its worker's last status, so the console's Ongoing list showed a worker's `needs-decision` as if it waited on the captain.
`edi steer --resolves <key>` recorded a resolved status as an event only (ADR-056), so the worker's last status kept showing the settled question until the worker reported again; in the shadow run a settled review gate stood for an hour while the worker simply ran its pipeline.

**Decision:** A status that is `needs-decision` or `blocked` is addressed to the primary (`StatusReport.isForThePrimary`).
The sitrep page shows such a worker's task as `with EDI`, doing "EDI is settling a pipeline question" for a key that names a pipeline gate (`nm-...`), "EDI is settling a question" for any other question, or "EDI is helping it past a block"; this replaces ADR-107's state and doing for those statuses.
When a worker acknowledges a message that resolves the key of its last status (`WorkerInbox.acknowledge`), that status becomes the resolved one (`Worker.settledBy`), which the page shows as working, "back at work; EDI settled its question"; a worker that reported something newer in between keeps its newer status.

**Motivation:** Only what waits on the captain looks as if it waits on him (ADR-156), and a question EDI settled stops showing once the worker has read the answer.

**Alternatives considered:**
- Clearing the question when the steer is sent - the worker has not read the answer yet, and steering can fail to arrive (ADR-056).
- Showing the worker's own text with the label - a status is written for EDI and may carry internal labels the captain should not see (ADR-128).

**Consequences:**
- Good: the console's Ongoing list never asks the captain for what the primary handles.
- Bad: a question the primary settles without `--resolves` keeps showing until the worker reports again.

**Revisit when:** The primary settles worker questions in some way other than a steer.

**Sources:** EDI rewrite specification, sections 10.2 and 12.4; ADR-055, ADR-056, ADR-107, ADR-128, ADR-156; `domain.status.StatusReport`, `StatusKey`, `domain.worker.Worker`, `application.worker.WorkerInbox`, `SteerWorker`, `web.console.SitrepConsolePresenter`.
