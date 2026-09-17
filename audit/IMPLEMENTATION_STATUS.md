# Repair implementation status

Implemented in `C:/Users/faust/source/repos/OCC_PRICER_Implementation`, branch `implement/repair-plan`, preserving the pre-existing build/export foundation from the audit checkout. The baseline audit remains historical evidence. This file distinguishes implemented code from release acceptance.

## Implemented and locally verified

| Findings | Changes |
|---|---|
| F01 | Removed desktop SMTP credentials and dependency path; user-mediated bug reports; packaged JAR checks exclude the old mail service. Provider revocation is external and still open. |
| F02, F34–F36 | Maven Wrapper, pinned dependencies/JDK release, generated application version, isolated package staging, payload checks, Windows/macOS CI, release drafts after both builds. Windows package built locally. |
| F03–F07, F12 | Shared settlement/CSV path; 19 named fields; real quote rates and bounties; exact split validation; proportional tender allocation; MISC separation; preserved split inputs; locale-independent money. |
| F08–F11 | Immutable identity/draft snapshots; one typed row model; stable line IDs; preserved finishes, provider markers and NM bases; condition-specific manual overrides; immutable provider catalog records with detached compatibility adapters. |
| F13–F15 | Atomic config/draft writes, legacy preference migration, revision-checked saves, previous config backup, explicit shared conflict state and guided local/shared comparison, legacy import and revision-checked resolution. |
| F16–F17 | Load last cache before refresh, coalesced refresh requests, serialized refresh/load, streaming JSON-array/JSONL import, unique temporary snapshots, gzip/record/count verification and atomic replacement. Failed loads preserve the in-memory index. |
| F18–F21, F29–F30, F32 | CSV mode parity/escaping/footer, etched exports, provider mapper shared across API/cache/drafts, validated name/code grammar, same quick-check pricing, bounded bounty/rule values and NM floor identity. |
| F22–F23 | Request/draft guards, cancelled search/import disposal, batch summary refresh, background finalization/autosave, history content preload, shared I/O outside summary calculation. |
| F24–F25 | Direct selected-record mapping, UUID trade/output names, SQLite approved snapshots/settlements/jobs, idempotent output retry and stored legacy receipt attachments. |
| F26–F28 | Bulk generation directories and manifests; observed worker exceptions/cancellation; preserved inventory edits on failed load; loaded-set filename tracking; exit protection; explicit snapshot confirmation. |
| F31 | Shared API limiter/headers, request timeouts and response bounds; bounded application task executor; image timeout/size/error handling. |
| F33 | Suffix-aware version comparison with prerelease ordering. |
| F37 | Recursive verified migration with per-file resumable manifest and retained originals. |
| F38 | Full receipt names/printing/condition, Unicode PDF outlines with exact ActualText, separate save/open outcomes. |

Current deterministic verification: **62 unit/regression/integration checks plus 1 shaded-artifact check**, no failures. Coverage includes the actual Swing trade panel, default check/bounty costs, negative/zero/stale splits, MISC, cent allocations, duplicate base/finish, locales, provider round trips, corrupt gzip, stale rate writers, same-size sync conflicts, restart/retry, actual process termination at precommit/postcommit/post-output boundaries, transaction rollback, cancelled/superseded presenters, Unicode PDF and resumable migration.

Delivery evidence: [clean verification log](implementation-verification.txt), [Windows packaging log](package-verification.txt) and [archive hashes/payload inspection](package-manifest.json). The Windows ZIP contains the exact verified JAR and no checkout or workstation data. Changes remain uncommitted on `implement/repair-plan` for review.

Dependency evidence: [resolved-version OSV query](dependency-scan.json) returned no advisories for the seven pinned runtime versions at the recorded timestamp. [Provenance hashes](dependency-provenance.json) match all three retained legacy dependencies against their resolved Maven artifacts; removed mail/activation libraries are unused. This is a timestamped scan, not a future vulnerability guarantee.

## Architecture delivered

- Typed `PrintingIdentity`, `Finish`, `Condition`, `TradeLine`, `Quote`, `TradeDraft` and `TradeTableModel` replace the parallel active trade lists with views of one row collection.
- `TradeEntryPresenter`, `TradeCustomerPresenter` and `TradeFinalizationPresenter` own lookup lifetime, customer/payment capture and approval. `TradeTableModel` owns typed table commands; TradePanel composes the existing Swing controls and legacy adapter.
- Immutable `ProviderPrinting` records own the shared index. Detached mutable Card adapters keep older screens compatible without exposing shared state. Quotes record observed provider prices, capture time and rate revision.
- Pure `SettlementEngine` returns immutable allocations; `TradePosEncoder` consumes them. `TradeApplicationService` validates/finalizes through `TradeRepository`.
- The workstation SQLite ledger stores approved snapshots, exact tender allocations, output bytes/hashes/status, legacy attachments and shared-copy jobs. Outputs can be regenerated without recalculation.
- `ProviderCardMapper`, `RateConfigurationRepository`, `SharedFolderSyncService`, `AtomicFiles`, `DataMigration`, `ProviderRequests` and `TaskCoordinator` establish boundaries for the incremental Swing application.

## Operational measurement

[Operational probe](operational-probe.json) ran under a **512 MiB heap** against a copied existing catalog and a synthetic 1,000-line Swing draft. It loaded **91,103** unambiguous catalog entries in **493 ms**, reloaded with the previous index retained in **396 ms**, and sampled a **226 MiB** peak heap. The 1,000-line batch took **51 ms**; summary median/P95 were **3/5 ms** and snapshot plus settlement took **40 ms**. These observations are from the recorded Windows/JDK machine, not a universal latency guarantee.

The existing legacy cache contained **6,795 ambiguous codes** caused by its lossy printing representation. They now require online lookup rather than choosing a record arbitrarily. This measurement uses legacy metadata, which is smaller than current full provider records. A fresh live bulk refresh remains an operational acceptance check. The probe is reproducible with `com.cardpricer.service.OperationalProbe` from `target/test-classes` plus the built JAR, an isolated `cardpricer.dataDir`, in-memory preferences and `-Xmx512m`.

## Release acceptance still required

1. **Account owner:** revoke the previously embedded app password and inspect/replace previously distributed packages. The session did not authenticate with it or change the provider account.
2. **POS owner:** supply accepted, anonymized files for every advertised mode and verify the provisional schema, finish/PLST aliases, tax/category constants, condition fields and cent-residual quantity splitting.
3. **Provider/live acceptance:** the official [Scryfall access guidance](https://scryfall.com/docs/faqs/i-m-having-trouble-accessing-the-scryfall-api-or-i-m-blocked-17) was retrieved through search after direct documentation access returned HTTP 403. It requires identifying request headers and traffic under ten requests per second. The shared client spaces requests by at least 500 ms, identifies the app, sets Accept, bounds responses/timeouts and retries at most once after 429. A current live bulk refresh and cancellation still need acceptance in the deployment environment.
4. **Platform/operational acceptance:** execute macOS CI packaging, physical printing and an interactive packaged desktop smoke test. Validate fresh full-metadata catalog peak memory and slow-share behavior against an owner-approved latency budget. Process-crash tests cover the transaction/output boundaries; physical disk-full and concurrent real-share failures remain environment checks.
5. **Product policy:** approve identification retention/shared-copy behavior, the documented condition floor policy and inventory snapshot semantics. Changes-only inventory mode is a separate product decision.

The code implementation and local verification are delivered. Production release remains gated on the acceptance items above. No production release, push or merge was performed; the original audit checkout was preserved.
