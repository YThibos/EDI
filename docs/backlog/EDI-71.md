# EDI-71 One effort word per plan

| Field | Value |
|---|---|
| Key | EDI-71 |
| Kind | ship, a change to the repository, on branch `feature/EDI-71` |
| Wave and order | wave 4, order 12 |
| Source | EDI-74 plan, step 19, with EDI-52 slice 18c |
| Size | S |
| Blocked by | [EDI-88](EDI-88.md) |
| Status | open |
| Issue | [#142](https://github.com/YThibos/EDI/issues/142) |
| Cloud session | ok |

## Goal

One effort word covers every slice of a plan and their relaunches, so the captain says it once per plan.

## Context

Since EDI-49, the word that lifts the effort ceiling is held for one task, one act and the current exchange, so eleven slices of one plan needed eleven authorisations.
EDI-52's slice 18c planned to let `--task` repeat; the lifecycle plan replaces it with an authorisation on the plan itself.
This item takes over EDI-52 slice 18c.
The research is in [the EDI-74 plan](research/EDI-74-plan.md), step 19, and [the EDI-52 plan](research/EDI-52-plan.md), finding F12.

## Acceptance criteria

- `edi captain said --plan <plan> --for effort-above-high --text "..."` records one authorisation on the plan, honoured for its slices and their relaunches.
- Every slice of the plan dispatches at the higher effort; a task outside the plan is still refused.
- The ceiling stays high everywhere else.
- `docs/decisions/captains-word.md` gains a history line.

## Requirements from decisions and answers

- Q10 A (EDI-74 round 1): one effort word per plan, for every slice and relaunch.
- The effort ceiling is high, and the word to lift it is the captain's, recorded verbatim, per act (I-14).
- The authorisation names the plan; it never widens to the project or to a later plan.

## Research and measurements

- The word was said once for eleven slices but held per task.
- Cost context: effort above high was 23.5% of the first week's worker spend with no measured return; EDI-97 will record what it buys.

## Read first

- [captains-word](../decisions/captains-word.md), [dispatch-routing](../decisions/dispatch-routing.md).
- `edi-core/src/main/java/io/github/ythibos/edi/domain/authority/` (`CaptainsWord`, `Authorisation`), `application/authority/`, `application/dispatch/DispatchTask.java`.

## How to verify

- `./mvnw verify`, then the native quick build (a command option changes).
- A dispatch test: both slices of a plan dispatch at the higher effort after one word; a third task outside the plan is refused.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status updated in the same pull request.
