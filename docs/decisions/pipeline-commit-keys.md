# The validation pipeline's own commits and pull request titles start with the work key, which EDI writes as a repository override at every draft-review launch

**Kind:** Design

## Context

Every commit subject starts with its work key (I-10), and the captain never squashes, so each commit the pipeline makes lands on the base as it is.
On no-mistakes 1.84.0 the pipeline titles its own fix commits from `commit.fix_message`, whose default is `no-mistakes({{.Step}}): {{.Summary}}`, and its pull request titles from `pr.title_format` ([docs/verification/no-mistakes.md](../verification/no-mistakes.md)).
`{{.Branch}}` with a `commit.branch_pattern` of one capture group puts the key in front, and a repository gets it from its own `.no-mistakes.yaml` on the default branch, from a `repository_overrides` entry in the pipeline's global `config.yaml` keyed by its remote, or from the global `commit` block; no `axi run` flag or environment variable sets it for one run.
A project without the setting had its fixes committed as `no-mistakes(test): ...`, and `{{.Summary}}` is the fix agent's own sentence, which once repeated the key and once held a whole report.
EDI already writes the pipeline's agent route into the same file at every launch ([pipeline-agent](pipeline-agent.md)).

## Decision

When `LaunchWorker` launches a worker whose brief delivers through the pipeline (`BriefFacts.deliversThroughThePipeline`, a `draft-review` ship), it reads the project clone's `origin` fetch URL and, when the project's registered forge serves it (`IsolatedCopies.forgeRemoteOf`, `RegisteredForge.isServedAt`), hands it to the `Pipeline` port's `keyCommitsPushedTo`.
`NoMistakesPipeline.keyCommitsPushedTo` has `CommitKeys` write one `repository_overrides` entry under that exact URL: `commit.branch_pattern` from `BranchName.workKeyCapture`, `commit.fix_message` `{{.Branch}} {{.Summary}}` and `pr.title_format` `{{.Branch}} {{.Title}}`.
The entry is keyed by the clone's own remote URL because the pipeline matches overrides against the remote it recorded for the repository, and outside a few public hosts a `.git` suffix or a different spelling is a different key.
`CommitKeys` first drops every entry whose remote names the same repository (`RemoteAddress.isTheSameRepositoryAs`), because two spellings that the pipeline normalises to one key make it refuse the whole configuration, and keeps every other repository's entry.
EDI owns the entry of each repository it launches work on, so a hand edit to it lasts only until the next launch, and a repository's own `.no-mistakes.yaml` still wins over it.
`PipelineConfiguration` carries the rules both writers share: it rewrites only one top-level entry, keeps every other line of the file, comments included, replaces the file atomically with its permissions, writes nothing when the entry already holds, never creates the file, and refuses the launch, naming the file, when it cannot read or replace it.
The pipeline intent ends its task sections with `## Commits`, which tells the pipeline's agents that the key is put in front of every commit subject and the title, so a summary or a title they write is one short line without it (`PipelineIntent.of`).

## Why

The fix happens where the subject is made, so no commit needs a rewrite afterwards, and no project needs a file of the pipeline's in its own repository.
Writing at launch keeps the entry true for every project EDI works on, the same way and at the same moment as the pipeline's agent route.
The branch pattern comes from the same domain type that names the branches, so a new branch kind keys its commits without a second edit.

## Alternatives rejected

- A global `commit` block set once by hand, with EDI refusing a launch the pipeline would not key - a manual step on every machine, and EDI would have to re-implement the pipeline's precedence rules to check it.
- A `.no-mistakes.yaml` committed in every project - it changes work repositories and takes effect only once merged to their default branch.
- Keying the entry by the forge's registered `https://` address - the pipeline would not match it to a clone whose remote is written another way.
- Rewriting the pipeline's commit subjects after its run - the captain asked for the fix at the source, and the commits are already pushed by then.
- The intent alone - the prefix comes from the template, which no prompt reaches.

## Consequences

- Good: every commit and title the pipeline makes on a project EDI delivers starts with the key, verified against no-mistakes 1.84.0's own configuration code.
- Good: tests use `FakePipeline`, and the test clones' remotes are local paths no forge serves, so no test writes a developer's pipeline configuration.
- Bad: EDI writes a second entry into another tool's configuration, so a change to `repository_overrides` on a later no-mistakes version needs `CommitKeys` to follow.
- Bad: a fix the pipeline makes on a branch without a key fails safely instead of committing, which matters only for work the captain runs through the pipeline by hand on such a branch.
- Bad: the intent can only ask the fix agent for a short summary; a summary that still repeats the key reaches the subject.

## When to revisit

When no-mistakes takes a commit or title format per run, or keys its commits from a work key it is given.

## How we got here

- 23/10/2026: a shadow run's fixes were committed as `no-mistakes(test): ...` and `no-mistakes(document): ...`, and earlier subjects on main repeated the key or held a whole report; the captain chose to have EDI write a repository override at every draft-review launch, alongside the pipeline's agent route, and to add the commits line to the intent (EDI-21).
