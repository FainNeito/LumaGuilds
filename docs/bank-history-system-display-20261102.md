# Bank history system display

## Spec
- WHEN a history transaction uses the reserved system actor UUID THEN the Java and Bedrock histories SHALL show the localized system label without looking up a player profile, while retaining the recorded description.
- WHEN Java history displays a withdrawal, fee or deduction THEN it SHALL show a debit sign; deposits SHALL show a credit sign.
- Existing player identities, persisted transaction data, filters and authority checks SHALL remain unchanged.

## Prove and engine
- TEST evidence: Market system deduction displayed `By: Unknown` and `Deduction - +$2`. Main source resolves the all-zero system UUID as a player and treats every type except withdrawal as a credit.
- A shared display helper handles the sentinel without player lookup and the Java debit sign; Bedrock already uses negative debit amounts and now shares the system-actor resolution. Focused tests cover system, named and unknown actors and every transaction type.

## Arch and refine
- Presentation only: no changes to bank accounting, persisted actors, provider API or transaction semantics. The historic system record does not identify an individual player and must not be attributed to one.
- Exact current-main tests, cross-plugin compatibility, hosted CI, TEST runtime adoption and native client acceptance remain pending. A fork forward-port is required; upstream and fork histories diverge. EARS/state helper availability not yet established.
- PR20 hosted build at e9b03f57 stopped before Guilds compilation: RoseChat dependency resolution queried the unrelated Iridium repository for dynamic Paper metadata and received HTTP 502. The build-only RoseChat init script now excludes the unrelated Paper and ProtocolLib groups from that repository while retaining dependency pins and other repository coverage. Hosted resolution and tests remain to be verified on the new head.
