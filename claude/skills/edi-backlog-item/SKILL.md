---
name: edi-backlog-item
description: >-
  Owns putting work on the EDI project on its upstream backlog: the GitHub issue that carries the item's full spec, its milestone, parent, blockers and labels, its key from the issue's number, and the hold on the local task, in one scripted step.
  Load it whenever EDI work is backlogged or queued for the cloud session, or when the captain says to put EDI work on the backlog; a half-formed idea is caught with `edi spark` instead.
user-invocable: true
---

# edi-backlog-item

Work on the EDI project goes to the backlog of its upstream repository, one GitHub issue per item, where the captain's cloud session picks it up, and is not dispatched on this machine.
`backlog-item.mjs`, beside this file, does every step; never open the issue, set its fields or queue its task by hand.
The pickup protocol the issue is taken by is [docs/pickup.md](../../../docs/pickup.md).

## Sparks first

An idea that is not thought through yet is no backlog item: catch it as a spark with `edi spark "<idea>"`, adding `--task` or `--project` when the task you acted on last is not what it is about.
The captain's message that opens with `Spark:` is caught by the hook already; acknowledge it in one line and carry on.
Sparks reach the backlog only when the weekly `gardening` round sorts them on the captain's word; an item drafted from a spark is followed by `edi spark backlog <n> <the item's key>`.

## The draft

Write one draft per item in a scratch file outside the repository: the fields between two `---` lines, then the spec.

| Field | Value |
|---|---|
| `task` | your local task on the EDI project, when one exists; without it, the task whose title matches is reused, or a new one is added with `edi task add --key EDI-<the issue's number>` once the issue is open |
| `title` | the item's title; the task's title when left out |
| `kind` | `ship` or `scout`; the task's kind, else `ship` |
| `branch` | `feature`, `chore` or `research`; `feature` for a ship and `research` for a scout when left out |
| `source` | where the work comes from, such as `The captain's word of 29 October 2026` |
| `size` | `XS`, `S`, `M` or `L`: under two hours, about half a day, about a day, several days of research |
| `milestone` | the title of an open milestone, the big improvement the item belongs to, or `-` |
| `parent` | the item this one is a slice of, as a key such as `EDI-105` or an issue such as `#155`; only where one item splits into slices |
| `blocked-by` | the items this one waits for, as keys or issues separated by commas, or `-` |
| `status` | `open`, or `held: <reason>` or `needs-captain-machine: <reason>` |
| `labels` | extra issue labels separated by commas, such as `security`; `cloud-ok` or `needs-captain-machine`, and `held`, come by themselves |
| `captain-approved` | only when the captain approved the item: where he said so, such as `the captain's word of 4 November 2026` |

The spec holds at least `## Goal`, `## Context`, `## Acceptance criteria`, `## Requirements from decisions and answers`, `## Read first` and `## How to verify`, and `## Research and measurements` where there is research, as the [issue template](../../../.github/ISSUE_TEMPLATE/backlog-item.md) lays them out.
With `task` named and no spec in the draft, the task's notes are the spec, and they must hold the same sections.
Restate the captain's answers as requirements, name no particular person, account, host or machine path, and write United Kingdom English, one sentence per line.
A research report too long for the issue goes to `docs/research` in its own pull request, and the spec links it.
A wait that is no dependency, such as "after a week of use", is a blocker on a marker issue the captain closes.

## The command

1. Run `node <this skill's directory>/backlog-item.mjs --dry-run <draft>...` and read every action it would take, including the issue body it would open.
2. Run it again without `--dry-run`.

It refuses a draft with a missing field or section, a spec that still holds a `TODO:` line, a milestone no open milestone carries, or a parent or blocker no issue carries, before it changes anything.
It opens the issue with its body, labels and milestone, titles it `EDI-<the issue's number> <title>`, makes it a sub-issue of its parent and marks it blocked by its blockers, and opens no pull request.
It adds `captain-approved` only when the draft names the captain's word; add that field only on his word, never on your own judgement, since the pickup counts an issue only when the captain's account added the label.
It queues the local task on the issue's key when none exists, and holds it for a deferral whose reason names the issue, so this machine never dispatches it.
A task that already carries a key keeps it, and its issue is titled with that key.
It goes to GitHub, through `gh api` only, as the account the clone is pinned to with the gh-account-pin skill, or as the active gh account when the clone has no pin.

## A batch

Pass several drafts to one run: each opens its own issue, in the order given, so a later draft may name an earlier one's key only once the earlier run has opened it.

## A rerun

A rerun is safe: an issue whose title carries the item's title or its task's key, a link, a label or a hold that already exists is reused, never duplicated, and an issue opened without its key in the title gets it.
Rerun the same command after a failure; it continues where the failure stopped.

## Reporting

- Tell the captain, per item, the key and title, the issue's full https URL, and that the local task is held.
- The cloud session takes an issue only once it carries `captain-approved` added by the captain's account, and `cloud-ok` without `held`.
