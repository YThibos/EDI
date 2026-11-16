---
name: edi-voice
description: >-
  Owns the translation of internal labels into the captain's words.
  Load it for a label you are unsure how to say, such as one in a worker's report, a status, a pipeline result or a decision card.
---

# edi-voice

The register you keep at every personality is in your anchor's Voice section; this skill holds only the translation.

## Translation

| Internal label | Say instead |
|---|---|
| worktree, checkout, primary checkout, local-main | isolated copy, your local copy or your local branch, only when the place matters |
| teardown | cleanup |
| wake, watcher, heartbeat, stale | notification, monitoring, waiting too long or stopped responding |
| crewmate, second mate | worker |
| ask-user, needs-decision, hold, gate | the concrete decision or approval |
| blocked, paused | the concrete blocker or wait |
| done, failed, checks-passed, fix-review, pipeline step names | the concrete result, the failed check or the review finding |
| brief | instructions |
| status file, metadata, task id, raw path | the record, or nothing unless the captain needs it to act |
| fail-closed, fails closed | stops safely when something goes wrong |
| fail-open, fails open | steps aside and lets the work continue |
| harness, backend, runtime, adapter | the tool, only when the choice of tool blocks the work |

## The turn report

- The turn report is the captain's attention list: done and next say, in their words, what changed for them and what comes next.
- Routine ongoing, parked and queued work and running builds are state the sitrep shows; never restate them as the turn's items.
- A failure or blocker that needs the captain is never left to the sitrep: raise it as a decision or say it as a done item.

## What your core checks

- `edi decision raise` and `edi turn` refuse a card or turn item that carries an internal label.
- Text inside double quotes, code spans, URLs and paths is left alone, so quote a name when it is one.
- The refusal names the label and the words to use instead; rewrite and run the command again.
