# Delivery

EDI never merges: every change reaches the captain as a draft the captain merges, or in the captain's own checkout on request ([ADR-022](decisions/ADR-022-drafts-the-captain-merges.md)).

## Delivery modes

A project's registry entry sets its `delivery` (see [Configuration](configuration.md#the-project-registry)):

- `draft-review`, the default: the validation pipeline pushes the branch and opens a draft merge or pull request, which the worker marks ready for review once its checks are green.
- `local-branch`: the work arrives in the project's `captainCheckout` by a ref-only fetch ([ADR-062](decisions/ADR-062-ref-only-delivery-into-the-captains-checkout.md)).
- `local-apply`, which one task may ask for with `--delivery local-apply`: the work arrives in the captain's checkout as an uncommitted patch.

Accepted work reaches the captain by its delivery mode ([ADR-084](decisions/ADR-084-accepted-work-reaches-the-captain-by-its-delivery-mode.md)).

## A ship's validation

A ship's brief names the exact validation pipeline call with the intent EDI wrote, craft rules included ([ADR-081](decisions/ADR-081-the-pipeline-adapter.md), [ADR-082](decisions/ADR-082-edi-writes-the-pipeline-intent.md)).
Its done counts only once its merge request is open, as a draft or ready for review, or merged, at the copy's head, and its branch adds no SNAPSHOT version ([ADR-153](decisions/ADR-153-a-ships-done-accepts-a-merge-request-that-is-a-draft-ready-or-merged.md)).
`gh` and `glab` must be logged in, and the validation pipeline must be installed for draft reviews (see [Tools EDI drives](tooling.md)).

## Drafts and merges

EDI records a task's merge request as soon as a pipeline run or a worker's status finds it on the forge, whether it is a draft, ready for review or already merged ([ADR-156](decisions/ADR-156-a-tasks-merge-request-is-followed-whatever-order-things-happen-in.md)).
EDI watches every open merge request, draft or ready for review, follows a merge the forge reports by refreshing the clone before any follow-up work, and cleans up the task once its work is landed ([ADR-087](decisions/ADR-087-merges-are-followed-from-the-forge.md), [ADR-088](decisions/ADR-088-follow-up-work-waits-for-the-refreshed-clone.md), [ADR-089](decisions/ADR-089-the-landed-work-test.md), [ADR-157](decisions/ADR-157-a-merge-the-forge-shows-hands-the-work-over-at-cleanup.md)).

```sh
edi task add --kind ship --project EDI --title "Fit the lamp" --key EDI-14 --delivery local-apply
edi deliver fit-the-lamp                            # open the draft from the pushed branch when the pipeline could not
edi merged fit-the-lamp                             # the captain merged it: check the forge, refresh the clone, clean up
edi merged https://github.com/owner/repo/pull/7     # the same, by the draft's address
edi cleanup fit-the-lamp                            # retire a landed task's copy, branch and workers
```

`edi deliver` opens the draft the pipeline could not ([ADR-085](decisions/ADR-085-edi-deliver-opens-the-draft-the-pipeline-could-not.md)).
`edi task show` names a ship's draft, its latest pipeline run and, once cleaned up, what it cost.
A cleanup that finds unlanded work lists every uncommitted path and unlanded commit and changes nothing.

## The publish check

The publish check refuses outgoing commits that carry a secret, a term of the publish deny-list or a personal path ([ADR-032](decisions/ADR-032-publish-guard.md)).

```sh
edi publish-check --range origin/main..HEAD         # the publish guard over outgoing commits
```

A push from a project's isolated copy, and the check in the project's registered clone, may still name that project's own forge host and account and, for a project tracked on JIRA, the tracker's host and the name of its keys file ([ADR-144](decisions/ADR-144-a-project-may-publish-its-own-forge-and-tracker-names.md)); every other term applies to every project.
