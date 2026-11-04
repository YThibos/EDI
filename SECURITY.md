# Security policy

## Reporting a vulnerability

Report a vulnerability privately, through GitHub's private vulnerability reporting: the repository's **Security** tab, then **Report a vulnerability**.
Never report one in a public issue, a pull request or a discussion.
Say what is affected, how to reproduce it, and what an attacker gains.
The report stays private until a fix has landed and the advisory is published.

## Supported versions

EDI has no releases: only the latest commit on `main` is supported, and a fix lands there.

## Scope

EDI runs on its user's own machine, and its core listens on loopback only ([api-access](docs/decisions/api-access.md)).
In scope are, among others:

- the core's API and console: authentication, the console session, the Host check and framing;
- the publish guard, which keeps secrets and personal material out of every push and every pull or merge request text ([publish-guard](docs/decisions/publish-guard.md));
- the pre-tool guard, the worker identity and the isolated copies that keep a worker inside its task ([pre-tool-guard](docs/decisions/pre-tool-guard.md), [isolated-copies](docs/decisions/isolated-copies.md));
- the build, the install script and the CI workflows.

Workers run as the same operating-system user as the primary session, so the pre-tool guard is a denylist and not a boundary; its records name the gaps it still has.
A bypass of a guard is still worth reporting, in particular one that a misled worker could reach by accident.
