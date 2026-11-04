# EDI-97 Effort and review yield recorded per task, shown in the Week view

| Field | Value |
|---|---|
| Key | EDI-97 |
| Kind | ship, a change to the repository, on branch `feature/EDI-97` |
| Wave and order | wave 6, order 35 |
| Source | EDI-75 change 3 |
| Size | M |
| Blocked by | [EDI-62](EDI-62.md) |
| Status | open |
| Issue | [#165](https://github.com/YThibos/EDI/issues/165) |
| Cloud session | ok |

## Goal

EDI records each task's effort and model beside its review rounds and findings, and the Week view shows cost and review yield per effort, so what extra effort buys can be judged on data after a month.

## Context

Effort above high was 23.5% of the first week's worker spend and all of it went to one large plan; the two merged slices at that effort took 1 and 3 review rounds (0 and 1 findings) against 2 to 5 rounds (1 to 7 findings) for the cheaper model at high.
The samples are too small and the tasks too different to compare, and nothing records whether it pays.
The pipeline's review at high effort also costs about 44% more per change.
The research is in [the EDI-75 measurements](research/EDI-75-measurements.md), sections 6, 8 and 10, change 3.

## Acceptance criteria

- The worker's effort and model are recorded with the task's review rounds and findings (`task_review`, `task_cost`).
- The Week view (EDI-62) shows cost and review yield per effort.
- A fixture of tasks at two efforts shows each effort's weighted tokens and findings per review round.

## Requirements from decisions and answers

- D46 A: ship changes 1 to 3 of the EDI-75 report; this is change 3, and it needs the Week view.
- Weighted tokens count input at 1, cache read at 0.1, cache write at 1.25 and output at 5, as the measurements did; keep raw counts.
- No dollar figure: the plan is not billed per token.

## Research and measurements

- 23.5% of weighted worker tokens went to the higher effort; high was 76.0%.
- Pipelines ran at medium 53.7% and high 46.3% in the first week.
- EDI's workers: 32 implementation workers on one model at high, 11 on another model at high, 4 at the higher effort, 8 research at high, 1 research at the higher effort.

## Read first

- [EDI-75 measurements](research/EDI-75-measurements.md), `edi-core/src/main/java/io/github/ythibos/edi/domain/delivery/TaskCost.java`, `PipelineReview.java`, `application/delivery/TaskCosts.java`, `adapter/pipeline/PipelineReviews.java`.
- [fleet-snapshot](../decisions/fleet-snapshot.md), and the Week view as built by EDI-62.

## How to verify

- `./mvnw verify`, then the native quick build (a migration).
- A cost test with two efforts, and a view test for the per-effort rows.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status updated in the same pull request.
