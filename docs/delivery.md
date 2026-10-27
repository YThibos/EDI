# Delivery

EDI never merges: every change reaches the captain as a draft the captain merges, or in the captain's own checkout on request, and an operation with nothing to commit is delivered by its report ([delivery](decisions/delivery.md)).

## Delivery modes

A project's registry entry sets its `delivery` (see [Configuration](configuration.md#the-project-registry)):

- `draft-review`, the default: the validation pipeline pushes the branch and opens a draft merge or pull request, which the worker marks ready for review once its checks are green.
- `local-branch`: the work arrives in the project's `captainCheckout` by a ref-only fetch ([delivery](decisions/delivery.md)).
- `local-apply`, which one task may ask for with `--delivery local-apply`: the work arrives in the captain's checkout as an uncommitted patch.
- `report`, which one task may ask for with `--delivery report`, for an operation on the machine with nothing to commit: the worker's report in the task directory is the delivery, its done counts once the report exists and the copy holds no change, and EDI then retires the task.

Accepted work reaches the captain by its delivery mode ([delivery](decisions/delivery.md)).

## A ship's validation

A ship's brief names the exact validation pipeline call with the intent EDI wrote, craft rules included ([worker-brief](decisions/worker-brief.md)), and EDI observes the pipeline ([supervision](decisions/supervision.md)) and sets nothing in it but its agent's model and effort ([pipeline-agent](decisions/pipeline-agent.md)) and the work key in front of its own commit subjects and pull request titles ([pipeline-commit-keys](decisions/pipeline-commit-keys.md)).
The intent carries the task's notes and the brief's spec, so the pipeline's review judges the change against what the worker was asked for, and EDI rewrites it, with the brief, whenever the task is noted ([worker-brief](decisions/worker-brief.md)).
Its done counts only once its merge request is open, as a draft or ready for review, or merged, at the copy's head, and its branch adds no SNAPSHOT version ([delivery](decisions/delivery.md)).
`gh` and `glab` must be logged in, and the validation pipeline must be installed for draft reviews (see [Tools EDI drives](tooling.md)).

## Drafts and merges

EDI records a task's merge request as soon as a pipeline run or a worker's status finds it on the forge, whether it is a draft, ready for review or already merged ([merge-following](decisions/merge-following.md)).
EDI watches every open merge request, draft or ready for review, follows a merge the forge reports by refreshing the clone before any follow-up work, and cleans up the task once its work is landed ([merge-following](decisions/merge-following.md), [cleanup](decisions/cleanup.md)).

```sh
edi task add --kind ship --project EDI --title "Fit the lamp" --key EDI-14 --delivery local-apply
edi deliver fit-the-lamp                            # open the draft from the pushed branch when the pipeline could not
edi merged fit-the-lamp                             # the captain merged it: check the forge, refresh the clone, clean up
edi merged https://github.com/owner/repo/pull/7     # the same, by the draft's address
edi cleanup fit-the-lamp                            # retire a landed task's copy, branch and workers
```

`edi deliver` opens the draft the pipeline could not ([delivery](decisions/delivery.md)).
`edi task show` names a ship's draft, its latest pipeline run, its active time and, once cleaned up, what it cost.
A cleanup that finds unlanded work lists every uncommitted path and unlanded commit and changes nothing.

## The publish check

The publish check refuses outgoing commits that carry a secret, a term of the publish deny-list, a personal path or a `-SNAPSHOT` version in a `pom.xml` ([publish-guard](decisions/publish-guard.md)).

```sh
edi publish-check --range origin/main..HEAD                                    # the publish guard over outgoing commits
edi publish-check --range origin/main..HEAD --excluding origin/feature/EDI-3   # leaving out what the remote branch holds
```

An isolated copy's `pre-push` hook checks only the commits a push adds to both its remote branch and the base, so a merged base or a rule tightened after an earlier push never strands the branch ([publish-guard](decisions/publish-guard.md)).

A push from a project's isolated copy, and the check in the project's registered clone, may still name that project's own forge host and account and, for a project tracked on JIRA, the tracker's host and the name of its keys file ([publish-guard](decisions/publish-guard.md)); every other term applies to every project.
