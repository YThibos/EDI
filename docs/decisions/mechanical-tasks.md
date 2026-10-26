# A mechanical task names its exact change, never touches protected files, and its done is verified before it counts

**Kind:** Design

## Context

The `mechanical` category runs on the cheapest model to save tokens, which is acceptable only where the work is foolproof by construction ([dispatch-routing](dispatch-routing.md)).
Quality is never traded for cost (I-17), so the cheap model's own claim that its work is right cannot be trusted.

## Decision

`edi dispatch --category mechanical` needs the change in `--spec`, every file it touches in `--files` (`ExpectedChange`), and the project's `verify` command in the registry.
It refuses files `ProtectedFile` recognises: decision records, migrations, security configuration, and tests with their expected values.
A mechanical worker's `edi status done` has its claims checked like any done ([delivery](delivery.md)), the inbox check and then a ship's delivery claims, and only then sets its done check to `checking`, and the supervision round runs `CheckMechanicalChange` off the scheduler thread: every file changed since `origin/<base>`, committed or not, must be named, at least one named file must change, and `sh -c <verify>` must succeed in the copy within 60 minutes, with its output in `mechanical-verify.log`.
An accepted check records `MechanicalChangeAccepted` and the work is delivered by its mode, so the mechanical check gates the delivery.
A refused check records `MechanicalChangeRefused` with the reason, closes the worker's window, and launches an `implementation` worker in the same copy, whose brief says why.

## Why

The cheap model gets only work a reviewer can verify at a glance, and nothing it touches beyond that is ever accepted silently.
Treating every test file as protected keeps a test from being bent to pass; a rename that reaches tests is implementation work.

## Alternatives rejected

- Running the verify command inside `edi status done` - the worker's call would wait for a whole build.
- Asking the worker for proof that verify passed - the check would trust the cheap model's own claim.

## Consequences

- Good: the guard needs no state beyond the worker's done check, and a crash mid-check leaves it to run again.
- Bad: a project without a `verify` command cannot use the category, and each check costs one full build.

## When to revisit

When the protected patterns refuse a change a reviewer would accept.

## How we got here

- 15/10/2026: the specification made `mechanical` foolproof three ways: an exact expected change, a full verify with a diff limited to the named files, and no protected file.
- 16/10/2026: slice S3 built the check and the re-dispatch as `implementation`.
- 26/10/2026: the record stated that an accepted check went on to the done's claims, so it now says that the claims are checked when the done is reported and the mechanical check gates the delivery (EDI-52).
