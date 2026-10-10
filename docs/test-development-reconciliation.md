# TEST development reconciliation

TEST-DEV-001: Preserve the latest development stack together with current canonical main fixes, existing TEST-only behavior, settings, permissions and persistence. These are unmerged TEST artifacts, not production releases.

Spec: combine the reviewed development branch with current main; do not replace live configuration or private artwork. Prove: run the full existing suite, native disposable MariaDB fixtures and actual companion JAR contracts. Engine: resolve overlap in favor of current main correctness while retaining new development features. Architecture: preserve application ports and infrastructure adapters. Refine: record exact build and deployment evidence in the operation receipt. Project-local EARS/state helpers are unavailable; this record is the manual requirement and task evidence.

Task: full checks, source PR, SHA-256 verification, rollback backup, TEST restart and startup inspection. Player/client acceptance remains separate.

## Verification evidence

- [x] Inspect current canonical main and the latest development stack; preserve original worktrees.
- [x] Preserve TEST-only behavior and publish a reviewable combined source PR.
- [x] Full local suite: {'tests': 1750, 'failures': 0, 'errors': 0, 'skipped': 20, 'exit': 0}; Java 25 plugin bytecode and the existing canonical build path.
- [x] Native disposable MariaDB and actual companion runtime contracts: {'tests': 12, 'failures': 0, 'errors': 0, 'skipped': 0, 'exit': 0}.
- [ ] Authorized TEST upload, backup, checksum verification and restart: tracked in the separate operation deployment receipt.
- [ ] Minecraft player/client acceptance, maintainer merge and production activation: separate gates.

Market command-scanning regressions register the search argument explicitly; production startup already registered it. Documentation requirement IDs distinguish search from accounting. Private artwork is not committed or replaced. Hosted final-head checks are reported independently from these local results.
