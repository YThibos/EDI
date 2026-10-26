# The validation pipeline's agent runs on the route the dispatch rules give it, which EDI writes into the pipeline's configuration at every launch

**Kind:** Architecture

## Context

The validation pipeline (no-mistakes) runs its own Claude Code agent for its review, test, documentation, lint and pull request steps, and started it with `claude -p` without a model or an effort, so it ran at the harness's global defaults.
On no-mistakes 1.84.0 the only way to give that agent a model and an effort is `agent_args_override.claude` in the pipeline's global `config.yaml`; the key is global-only, `axi run --model` and `--effort` serve the Pi agent only, and the daemon runs every step in its own environment ([docs/verification/no-mistakes.md](../verification/no-mistakes.md)).
Until this decision EDI only read the pipeline's state ([supervision](supervision.md)).
Effort has three levels and `high` is the ceiling (I-14).

## Decision

`config/dispatch-rules.json` carries a `pipeline` entry, `{ "use": { "model", "effort" }, "why" }`, checked like a category ([dispatch-routing](dispatch-routing.md)): a reason is required, the model must be on the machine profile's allow-list, and the effort must be low, medium or high, or absent for a model that ignores it (`PipelineAgent`, `DispatchRules.validatedAgainst`).
The rules are refused without a `pipeline` entry, so the pipeline's agent never runs at the harness default; `examples/edi-home/config/dispatch-rules.json` routes it to Opus 5.5 at high effort.
`LaunchWorker` resolves the pipeline's route from the rules at every launch, the first and every later one, and hands it to the `Pipeline` port's `runAgentOn`, because a worker drives the pipeline and every launch reads the rules as they are then.
`NoMistakesPipeline.runAgentOn` sets `agent_args_override.claude` in `<pipeline home>/config.yaml` to `--model <model>`, followed by `--effort <effort>` when the route names one.
`AgentArguments` rewrites only that top-level entry, keeps every other agent's arguments within it and every line of the file outside it, comments included, replaces the file atomically with its permissions, and writes nothing when the arguments already match.
A configuration EDI cannot read as YAML or cannot replace refuses the launch, naming the file, and leaves no temporary file beside it.
EDI never creates the pipeline's configuration: a pipeline home without `config.yaml` is left alone.
EDI owns `agent_args_override.claude`, so a hand edit to it lasts only until the next launch.
The migration from the old harness adds the example's `pipeline` entry to a rules file that lacks one (`DispatchRulesFile.withWhatItLacks`).

## Why

The model and effort of every agent EDI depends on come from the one file the captain reads and edits, and the pipeline's reviewer, the last judge before the captain's own review, never runs at whatever the harness defaults to.
Writing at launch keeps the pipeline on the rules as they are when the worker that drives it starts, without a new command or a watcher.

## Alternatives rejected

- Leaving the pipeline at the harness default - the captain asked for an explicit model and effort.
- Keeping a hand-written `agent_args_override` - the rules file and the pipeline's configuration would drift apart without anything noticing.
- Passing the model and effort through `axi run` or the worker's environment - `axi run` takes them only for Pi agents, and the daemon does not see the caller's environment.
- Rewriting the whole configuration through a YAML mapper - the captain's comments and every other setting's layout would be lost.
- Creating the configuration when it is missing - EDI never creates another tool's configuration, and the first launch after the pipeline is set up writes the entry.
- Writing at core start or every supervision round - a launch is when a worker that will drive the pipeline appears, and it already reads the rules.

## Consequences

- Good: one edit to the rules changes the pipeline's agent at the next launch, and a missing or invalid `pipeline` entry refuses the dispatch, naming the file.
- Good: tests use `FakePipeline`, and the native tests give the core a pipeline home of their own, so no test writes the developer's pipeline configuration.
- Bad: EDI writes into another tool's configuration, so a change to that key on a later no-mistakes version needs `AgentArguments` to follow.
- Bad: the pipeline is shared by every project, so its agent runs on one route for all of them, and a run that has already started keeps the arguments it started with.
- Bad: an EDI home's rules file needs the `pipeline` entry before a core with this decision reads it, or every reader of the rules refuses, the outage list included.

## When to revisit

When no-mistakes takes a model and effort per run or per repository for Claude Code, or a project needs its own pipeline route.

## How we got here

- 23/10/2026: the pipeline's agent had run at the harness default, Opus at medium; the captain asked for an explicit model and effort from the dispatch rules, high effort either way, and Opus 5.5 stayed the model; a hand-written `agent_args_override` served as the stop-gap until EDI took the entry over (EDI-46).
