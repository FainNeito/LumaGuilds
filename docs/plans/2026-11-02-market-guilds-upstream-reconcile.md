# Guilds upstream reconciliation for Market acceptance

## Scope and requirements

Preserve the integrated TEST candidate and the bank-history correction while incorporating the authoritative upstream guild-creation payment changes. The candidate must retain schema 49, cooldown bypass checks, the shop XP API and the upstream Vault founding-payment protections. A successful merge alone does not establish compatibility or permission to deploy.

## Evidence and conflict decisions

- Integrated candidate parent: `478475fca060920d1cdc6c0b46183e4aa0fc4943`; upstream main parent: `c0a144bf253835361d3cf2439ec25f21ddb417f2`. Fork main `e118d45799223cb974de320fe1903e98dbdc4a55` is already an ancestor of the candidate.
- The workflow conflict retains the existing full test, probe and review-artifact steps and adds upstream's disposable MariaDB service and SQL test step.
- The creation-flow test conflict uses the upstream Vault journal and account payment while retaining the candidate's ordinary, authorized bypass and denied-bypass checks, including refund and persistent cooldown assertions.
- The Vault journal creates its own tables idempotently; it does not claim a numbered migration. Existing migrations through version 49 remain present. This is source inspection, not a live migration rehearsal.

## Remaining verification

Run full exact-head tests and packaging, including the new combined creation flows and native MariaDB contracts. Review both parent diffs, currency settlement, schema migration/rollback, companion APIs, pack dependencies, PR stack and current upstream/fork heads. Obtain review and publish through the appropriate PR path. No runtime, client, production or deployed-artifact acceptance follows from this reconciliation alone.
