# EDI-1 Clear the high-severity vite advisory in the console's lockfile

| Field | Value |
|---|---|
| Key | EDI-1 |
| Kind | ship, a change to the repository, on branch `chore/EDI-1` |
| Wave and order | no wave |
| Source | Dependency upkeep |
| Size | XS |
| Blocked by | nothing |
| Status | delivered |
| Issue | [#134](https://github.com/YThibos/EDI/issues/134) |
| Cloud session | no: the check needs the captain's machine |

## Goal

Clear the high-severity advisory in the console's lockfile, so the console's dependency audit passes again.

## Context

The audit at high severity fails on the main branch: the build tool's bundled dev server in the console's lockfile carries advisories in a nested dependency (launch-editor), which blocks every pull request's checks.
The captain merged the first review and asked for the fix straight away, and the task has been held since 22 October 2026.
EDI-1 is also the evergreen work key for dependency upkeep.

## Acceptance criteria

- The console's high-severity audit passes: `npm audit --audit-level=high` in `edi-console` reports nothing at high or above.
- The console's `package.json` overrides no `vite`: the Angular build brings the fixed Vite.
- The lockfile changes only as far as the fix needs.
- The console still builds and its tests pass.

## Requirements from decisions and answers

- Option A of decision D2 (captain, 21 October 2026): fix straight away; the review of the first attempt was merged.
- Option B of decision D6 (captain): no version pin, so the `vite` override goes as soon as the Angular build brings the fixed Vite.
- Never use `--force` on the audit fix without reading what it changes; a major bump of the build tool is a scope change, so ask on the issue first.
- The console's dependencies are checked against upstream releases, not guessed.

## Research and measurements

- The advisories concern a transitive dependency of the console build tool's dev server: GHSA-698v-4r3x-3vcv, GHSA-r825-w8mm-5pxw and GHSA-587h-7wmj-h5pm, against vite 8.3.0 under `@angular/build` 22.2.2 in `edi-console/package-lock.json`.
- The upstream fix is `@angular/build` 22.2.5, which depends on vite 8.3.5, past the fix in vite 8.3.3; `@angular/build` 22.2.4 still depends on vite 8.3.0.
- The console therefore asks for `@angular/build` and `@angular/cli` at 22.2.5 or above, and Renovate's Angular group update to 22.2.5 is the same change.

## Not for the cloud session

The check needs the captain's machine, for the captain's registry access and lockfile state.

## Read first

- `edi-console/package.json`, `edi-console/package-lock.json`, [dependency-upkeep-and-scanners](../decisions/dependency-upkeep-and-scanners.md), [development](../development.md).

## How to verify

- `npm audit --audit-level=high` in `edi-console`, then `./mvnw verify`.

## Delivery

Deliver this item as the [pickup protocol](README.md#pickup-protocol) says: a draft pull request that names the item and closes its issue, local verification in the pull request body, a question with lettered options instead of a guess, and this file's status updated in the same pull request.
