# ADR-020: The core listens on loopback only and authenticates every caller

**Date:** 15/10/2026

**Status:** Accepted

**Kind:** Architecture

**Context and constraints:** The core's API changes the fleet's state and answers the captain's decisions, and the captain's word is the authority for destructive and irreversible actions (I-06).
Any page open in the captain's browser could otherwise submit requests to a local port.

**Decision:** The core binds `127.0.0.1` only, on the port in the machine profile (default 47318), and writes the port it bound to `$EDI_HOME/state/core.port`, where the command finds it.
Every API call carries a bearer token scoped to its caller (the primary, each worker, each hook role), or the captain's console session cookie, which is HttpOnly and SameSite=Strict and issued only through the one-time token that `edi console` opens.
State-changing console requests also carry a custom header the core checks.
Slice S0 delivers the loopback binding, the port file and the unauthenticated health endpoint `/api/health`; tokens arrive with the API in slice S1.

**Motivation:** No port is reachable from outside the machine, workers and hooks can never act as the captain, and no other page in the browser can submit on his behalf.

**Alternatives considered:**
- A Unix socket for the command - simpler for the command, but the console needs HTTP anyway.

**Consequences:**
- Good: one transport for the command, the hooks and the console.
- Bad: token issuance and scoping are code EDI owns.

**Revisit when:** EDI serves anything beyond the local machine.

**Sources:** EDI rewrite specification, sections 5.1, 7.3, 9.1 and 16; `edi-core/src/main/resources/application.yaml`, `EdiCoreApplication.corePortFile`, `edi-cli/src/main/java/io/github/ythibos/edi/cli/CoreAddress.java`.
