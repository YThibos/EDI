# ADR-017: The harness is person- and employer-agnostic

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** EDI serves one developer on two machines, one of them an employer's laptop, from one public-ready codebase.
The captain wanted the harness agnostic of both machines, "and thus might even be re-usable for those who want to try it out", with personal configuration outside the tracked code, following the upstream pattern of gitignored personalisation directories.

**Decision:** Tracked files hold generic code, defaults and documented examples only.
Everything about the captain, an employer or a machine lives in the EDI home (`$EDI_HOME`, default `~/.edi`: machine profile, project registry, dispatch rules, personal settings, publish deny-list, memory, store and state) or in the checkout's gitignored `personal/` directory (personal skills and addenda to the anchor and briefs).
The EDI home is private, never a git repository and never shared between machines; `edi personal export --machine-neutral` and `edi personal import` move the machine-neutral part.
The publish guard refuses any push that would carry personal material (ADR-032).

**Motivation:** The same repository serves both machines and anyone else, and nothing personal can leak through the code itself.

**Alternatives considered:**
- Personal configuration committed to a private repository - ties the harness to one person and one employer.

**Consequences:**
- Good: decision records, tests and examples use invented names only.
- Bad: setting up a machine needs the EDI home's configuration, which the repository cannot ship.

**Revisit when:** Never for the principle.

**Sources:** EDI rewrite specification, sections 6, 7.1 to 7.5 (decision E8, I-38); `.gitignore` (`personal/`), `EdiHome`.
