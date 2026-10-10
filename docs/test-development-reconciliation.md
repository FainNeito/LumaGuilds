# TEST development reconciliation

TEST-DEV-001: Preserve the latest development stack together with current canonical main fixes, existing TEST-only behavior, settings, permissions and persistence. These are unmerged TEST artifacts, not production releases.

Spec: combine the reviewed development branch with current main; do not replace live configuration or private artwork. Prove: run the full existing suite, native disposable MariaDB fixtures and actual companion JAR contracts. Engine: resolve overlap in favor of current main correctness while retaining new development features. Architecture: preserve application ports and infrastructure adapters. Refine: record exact build and deployment evidence in the operation receipt. Project-local EARS/state helpers are unavailable; this record is the manual requirement and task evidence.

Task: full checks, source PR, SHA-256 verification, rollback backup, TEST restart and startup inspection. Player/client acceptance remains separate.
