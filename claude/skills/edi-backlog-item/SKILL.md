---
name: edi-backlog-item
description: >-
  Owns putting work on the EDI project on its upstream backlog: the item's spec in docs/backlog, its index row, the pull request that adds them, its GitHub issue and the hold on the local task, in one scripted step.
  Load it whenever EDI work is backlogged or queued for the cloud session, or when the captain says to put EDI work on the backlog.
user-invocable: true
---

# edi-backlog-item

Work on the EDI project goes to the backlog of its upstream repository, where the captain's cloud session picks it up, and is not dispatched on this machine.
`backlog-item.mjs`, beside this file, does every step; never write the item file, the index row, the pull request or the issue by hand.

## The draft

Write one draft per item in a scratch file outside the repository: the fields between two `---` lines, then the spec.

| Field | Value |
|---|---|
| `task` | your local task on the EDI project, such as `edi-work-goes-to-the-upstream-backlog`; without it, the task whose title matches is reused, or a new one is added with `edi task add`, which allocates the next free `EDI-<n>` key |
| `title` | the item's title; the task's title when left out |
| `kind` | `ship` or `scout`; the task's kind, else `ship` |
| `branch` | `feature`, `chore` or `research`; `feature` for a ship and `research` for a scout when left out |
| `source` | where the work comes from, such as `The captain's word of 29 October 2026` |
| `size` | `XS`, `S`, `M` or `L`, as the index defines them |
| `section` | the start of an index section's heading: `Wave 1` to `Wave 6`, `Security` or `Held`; the item's row goes into that section's table |
| `severity` | the finding's severity; required in the security section |
| `blocked-by` | work keys such as `EDI-85`, separated by commas, or `-` |
| `status` | `open`, or `held: <reason>`, `parked: <reason>` or `needs-captain-machine: <reason>` |
| `labels` | extra issue labels separated by commas; the section, `cloud-ok` or `needs-captain-machine`, and `parked` come by themselves |

The spec is the item's body in the style of the items in `docs/backlog`, and holds at least `## Goal`, `## Context`, `## Acceptance criteria`, `## Requirements from decisions and answers`, `## Read first` and `## How to verify`.
With `task` named and no spec in the draft, the task's notes are the spec, and they must hold the same sections.
Restate the captain's answers as requirements, name no particular person, account, host or machine path, and write United Kingdom English, one sentence per line.

## The command

1. Run `node <this skill's directory>/backlog-item.mjs --dry-run <draft>...` and read every action it would take, including the item file it would write.
2. Run it again without `--dry-run`.

It refuses a draft with a missing field or section, or a spec that still holds a `TODO:` line, before it changes anything.
It refuses an item whose key already names other work upstream, in an index row or an issue title, before it changes anything upstream.
It fetches the EDI project's clone in the EDI home, works in a throwaway copy of it from a fresh `origin/main`, or from the item's branch when an earlier run pushed it and it has not merged, and removes the copy afterwards.
It puts the item's row at the end of its section's table with the highest order plus one in the whole index, on `main` and on every branch awaiting merge, so every order stays unique and no other row changes; in the held section, whose rows carry `-` for their order, the row carries `-` too.
It opens the pull request ready for review on `chore/<the first item's key>`, opens one issue per item, names each issue in its item file's `Issue` field in a second commit, and holds each local task for a deferral whose reason names the issue and the pull request.
The index row carries no status and no issue: the item file is the only place for both, and `node docs/backlog/board.mjs` prints them beside the index.
It goes to GitHub as the account the clone is pinned to with the gh-account-pin skill, or as the active gh account when the clone has no pin.

## A batch

Pass several drafts to one run: they share one branch, keyed by the first item, and one pull request, with one commit and one issue per item.

## A rerun

A rerun is safe: an item file, an index row, an open pull request, an issue or a hold that already exists is reused, never duplicated.
Rerun the same command after a failure; it continues where the failure stopped.

## Reporting

- Tell the captain, per item, the key and title, the pull request's full https URL and the issue's full https URL, and that the local task is held.
- The captain merges the pull request; you never merge it, and never push to `main`.
- The cloud session takes an open item only once its pull request has merged, because it reads the index on `main`.
