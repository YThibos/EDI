# ADR-139: The old home's configuration becomes EDI's by adding what EDI lacks, and every gap is listed rather than guessed

**Date:** 19/10/2026

**Status:** Accepted

**Kind:** Design

**Context and constraints:** Section 18 turns the old registry `data/projects.md` into `config/projects.yaml` without the old harness's own entry, compacts `config/crew-dispatch.json` into the eight categories and the EDI override of section 8.6 with each old rule's evidence carried into its category's reason, sets `craftRules` from `config/craft-rules-projects` and writes the rules text to `memory/craft-rules.md`, carries `config/fleet-ledger`, `config/startup-memory-budget` and `FM_PAUSE_RESURFACE_SECS` into the machine profile, and writes `config/personal.yaml` and `config/publish-denylist`.
The session-start digest is clean only when every file `HomeSetup` expects exists (ADR-132).
The old registry is prose: each line names a project, a mode of the old harness and a description that says `base origin/<branch>;`, and nothing in it names a forge account or a tracker key.
A forge entry without its account makes the whole registry unreadable (`RegisteredForge`), and the cutover home already holds the shadow run's validated registry, machine profile and personal configuration.
Tracked files never name the captain, the employer, an account or a host (ADR-017).

**Decision:** `HomeImportPlanner` computes each configuration file as the EDI home's own content plus what the old home holds that EDI lacks; it never changes a value EDI holds.
- `RegistryImport` adds each old project EDI does not register, except the old harness itself: its base from `base <branch>;`, its forge kind, host and path from its clone's `origin` (`github.com` is GitHub, any other host GitLab), its account only from a project EDI registers on the same host and namespace, `draft-review` delivery, or `local-branch` when its clone has no remote, `craftRules` from the old list, and `tracker: { kind: edi, projectKey: EDI }` for EDI itself (decision E7); a forge without a known account, a missing base, a clone without a remote and a missing tracker are left out of the entry and listed with the line to add.
- `ProjectRegistry.contentWith` appends the new entries as text below the captain's own, rendered from the same records the registry reads, so the captain's lines and comments keep their bytes; only when the appended file would not read back as the registry plus the new entries, such as a list written in flow style, is the registry rewritten whole, and a registry that gains nothing keeps its bytes.
- `config/dispatch-rules.json` is the example the repository ships (`examples/edi-home/config/dispatch-rules.json`, packaged into the core), with each old rule's reason appended to the reason of the category or override `RoutingRulesImport` maps it to by a phrase of its `when`; the old harness's own rules and any rule no phrase recognises carry nothing and are listed, and their text stays in the archived file.
- `MachineProfile.contentWith` adds only the settings the profile lacks: the example profile's worker models, `time.fleetLedger`, `memory.startupBudgetTokens` and `supervision.pauseResurface` (`ForkEnvironment` reads the variable the old harness was started with); a profile without a tracker section is listed.
- `config/personal.yaml` is written with the address word only when the home has none; the old harness keeps no presence settings, and EDI reads no policy values there yet.
- `config/publish-denylist` keeps the captain's terms and adds, by a case-insensitive comparison, every forge host the registry or an old clone's remote names outside the public forges, the accounts registered on those hosts, the tracker's host and the name of its keys file (`DenyListImport`).
- `memory/craft-rules.md` is the block the old `bin/fm-craft-rules.sh` prints from its `print_rules` function; when neither home holds one, the note says so.

**Motivation:** Taking only what the old home states, and naming every missing field with the exact line to write, keeps the registry readable and the captain in charge of each account and tracker, while the validated configuration of the shadow run survives the import.

**Alternatives considered:**
- Guessing an account from a namespace owner or a tracker key from the old prose - a wrong account sends forge calls under the wrong identity, which the captain's account rules forbid.
- Rewriting each configuration file from the old home - would discard the shadow run's validated JIRA settings.
- Writing the policy values of section 7.1 into `personal.yaml` - nothing reads them yet, and the captain does not want configuration kept just in case.
- Rewriting the whole registry from its parsed form - would turn the captain's flow maps into blocks and drop every comment.
- Writing the folder map of the time-booking tool - it lives outside the EDI home, in the tool's own configuration, which this harness does not touch.

**Consequences:**
- Good: a home without configuration gets every file the digest expects, and a dispatch validates against the written models and categories.
- Bad: projects added without an account or tracker cannot ship until the captain completes their entries.
- Bad: a machine profile that gains a setting is written again from its parsed form, which drops its comments; the earlier file is kept beside it (ADR-136).

**Revisit when:** The old registry is gone, or `personal.yaml` gains a policy EDI reads.

**Sources:** EDI rewrite specification, sections 7.1, 8.4, 8.6, 13.5 and 18; session prep report, Appendix D, F4 and B2; ADR-017, ADR-032, ADR-041, ADR-050, ADR-132; `application.migration.HomeImportPlanner`, `RegistryImport`, `RoutingRulesImport`, `DenyListImport`, `ExampleConfiguration`, `adapter.registry.ProjectRegistry`, `NewProject`, `adapter.home.MachineProfile`, `CarriedMachineSettings`, `PersonalConfiguration`, `DispatchRulesFile`, `adapter.fork.ForkHome`, `ForkProject`, `ForkRoutingRulesReader`, `ForkEnvironment`, `adapter.git.Git`; AT-47.
