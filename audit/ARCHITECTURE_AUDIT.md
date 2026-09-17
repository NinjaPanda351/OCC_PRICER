# OCC_PRICER architecture and defect audit

Date: 2026-09-15  
Repository: NinjaPanda351/OCC_PRICER  
Baseline: master, commit dcf68e64ef8a845f63ae4429360dc52b1cfb8c21, application version 4.1.6  
Audit checkout: C:/Users/faust/IdeaProjects/OCC_PRICER_Audit

## Executive assessment

The highest risks are incorrect POS output, loss of trade identity and recovery state, and an embedded email credential. The recurring architectural problem is that UI controls, mutable card objects, parallel lists, configuration instances, receipts, and CSV exporters each hold their own interpretation of the transaction.

The application can be repaired incrementally while retaining Java and Swing. The priorities are: contain the credential exposure; establish executable correctness checks; unify identity, pricing and payment calculations; make committed trades durable and retryable; then simplify the screens and background work.

This audit records **38 findings**. They include reproduced defects, source-confirmed defects, and explicitly identified design risks. Several findings contain related manifestations of the same cause. The companion [repair plan](REPAIR_PLAN.md) defines the work sequence and acceptance criteria.

## Scope and evidence

- Inventoried 44 Java source files, both build scripts, IntelliJ metadata, release workflow, changelog and bundled dependencies.
- Reviewed the main trade, catalog/API, pricing, session recovery, CSV, history, inventory, import, configuration, printing and update paths.
- Compiled every production Java source with JDK 25.0.1 and --release 25: successful. -Xlint:all reported 70 warnings, mostly serialization, constructor escape and deprecated API warnings. These warnings are not the basis for the critical findings.
- Ran **29 isolated baseline defect probes** against the compiled code. All confirmed the expected defects. See [probe output](probe-results.txt) and [probe source](AuditProbes.java).
- Probes use synthetic data, in-memory Java preferences and an isolated APPDATA directory under out/. Swing component checks run headlessly on the event dispatch thread. Reflection is used only by this audit harness.
- The existing OCC_Trade_Pricer working copy and its runtime records were preserved. Production source and release configuration in the audit clone were not edited.
- This is a code audit with focused reproductions, not a certification that every defect has been found. A real POS import, packaged desktop launch, physical printing, macOS packaging, large live catalog refresh, network-share fault test and SMTP credential validity check were not performed.

Source links below are pinned to the audited commit. **R** means reproduced in the harness; **S** means directly supported by source; **Risk** means a failure scenario or product decision requiring further validation.

## Architectural problems

| Area | Current structure and consequence | Target |
|---|---|---|
| Trade state | TradePanel is 2,637 lines. It maintains receivedCards, cardConditions, rowPayouts and a DefaultTableModel independently. Formatted prices are parsed back into calculations. | One typed TradeDraft with stable line IDs; a table model that renders it; application commands for edits. |
| Pricing and payment | PricingService, BuyRateService, PaymentTypePanel, TradePanel and the POS exporter each perform pieces of money calculation. Quick checks use raw prices while trades use rounded prices. | One pure quote/settlement engine; all views and exports consume its immutable result. |
| Printing identity | Card stores mutable display-oriented identifiers; boolean foil and string finish can disagree. Collector markers and PLST identity are stripped. | Preserve provider ID, raw set/collector number, finish and language; map to POS codes only at export boundaries. |
| Persistence | Human-readable receipts act as the history database; autosave is a lossy UI snapshot; multi-output completion has no durable transaction state. | Versioned draft snapshots, structured committed trades, unique IDs and persistent export/sync tasks. |
| Dependencies | Services import PreferencesPanel to access configuration; UI constructors instantiate services; the model CardEntry constructs PricingService and formats CSV. | Bootstrap dependencies in one place; domain and application code depend on interfaces, not Swing. |
| Concurrency | Independently created SwingWorkers, shared-file polling in summary calculation and lifecycle-blind callbacks. | A bounded task coordinator with cancellation, request generations and immutable snapshots. |
| Delivery | Hand-built fat JARs, vendored libraries, broad package input and tag-only release automation. | A reproducible build, isolated staging directory, PR checks and tested packaging on supported platforms. |

The existing Trade model describes a two-sided trade but the active receiving UI owns its transaction separately. Moving methods between large panels without unifying state would leave the main failure modes intact.

## Findings

### Immediate containment and transaction correctness

#### F01 — P0: An email app password is embedded in the application [S]

[HelpEmailService.java:14](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/HelpEmailService.java#L14) declares a literal app password and uses it for SMTP authentication. Compilation embeds it in distributed bytecode as well as source/history. Its current validity was not tested.

**Impact:** Anyone with access to a source copy or built application can recover the credential and potentially use the account if it remains active.

**Fix:** Revoke it at the provider first. Replace shared desktop SMTP authentication with a user-mediated report mechanism, or an authenticated server endpoint whose credentials remain on the server. Remove the literal, inspect released artifacts and history, and add secret scanning. History cleanup alone does not revoke copies already distributed. Account action is owner work; this audit did not change the account or contact anyone.

#### F02 — P1: Packaging takes the entire repository as application input [S, Risk]

Both [build.ps1:120](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/build.ps1#L120) and [build.sh:109](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/build.sh#L109) pass the project root to jpackage --input. Source, IDE files, dependencies, Git metadata and any runtime data under that directory become eligible for packaging. Output is also placed under the input tree, complicating repeat builds. Existing .gitignore does not exclude data/ or the root application JAR.

**Fix:** Stage only the application artifact, required runtime assets and notices in a dedicated directory; package from that directory into a separate output directory. Validate artifact contents automatically. Oracle documents that all files in the input directory are packaged. [jpackage documentation](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html)

#### F03 — P1: POS CSV rows have 18 columns under a 19-column header [R]

[TradeReceivingExportService.java:193](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/TradeReceivingExportService.java#L193) omits the placeholder for QTY ON HAND. The probe produced:

| Header position, one-based | Expected field | Actual value in a $100 check export |
|---|---|---|
| 13 | QTY ON HAND | 33.33, the cost |
| 14 | COST | empty |
| 16 | BID | 33.33, the extended cost |
| 17 | EXTENDED COST | TAX |
| 18 | TAX CODE | 100.00 |
| 19 | PRICE | absent |

**Fix:** Define an explicit POS schema and populate named fields through one CSV encoder. Verify both field count and semantic position against a fixture accepted by the actual POS. The current header mismatch is proven independently of which POS product is used.

#### F04 — P1: POS costs disagree with actual payouts [R, S]

[TradeReceivingExportService.java:173](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/TradeReceivingExportService.java#L173) applies fixed 50% credit, price/3 check and 41.67% partial rates. [BuyRateService.java:44](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/BuyRateService.java#L44) defaults to 40% check and supports tiers/bounties.

Reproduced: a $100 default check trade quotes $40.00 but exports cost $33.33; an 80% bounty quotes $80.00 credit but exports $50.00. Partial export receives no actual split amounts, so it cannot derive the actual acquisition cost.

**Fix:** Compute a settlement once, with per-line amounts and chosen tender allocation. Use it for receipt, totals and POS cost. Define how mixed payment and excluded MISC value are allocated. Do not merely replace one fixed constant.

#### F05 — P1: Finalization writes receipts before validating the whole transaction [S]

[TradePanel.java:2118](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/TradePanel.java#L2118) calls saveList() before exportToPOS(). Split validation occurs in exportToPOS(), while saveList() tolerates invalid input by using zeros and deletes autosave at [TradePanel.java:2190](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/TradePanel.java#L2190).

If validation or export fails, a receipt may already exist and be copied to the shared folder. Retrying creates another timestamped receipt. An all-MISC trade saves its receipt, then exportToPOS() returns false, preventing normal completion.

**Fix:** Commit active cell editors, snapshot and validate every field first. Persist one uniquely identified trade and retryable output tasks. Separate saved-trade state from export/sync status; handle a legitimate zero-POS-row trade explicitly. Preserve recovery until a durable state can reconstruct the work.

#### F06 — P1: Split payment permits invalid amounts [R, S]

[TradePanel.java:1985](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/TradePanel.java#L1985) checks only an allocation equation with a $1 tolerance. There are no non-negative amount checks. For a $100 trade with 50% credit/40% check rates, -$10 credit plus $48 check covers exactly $100 under that equation. The harness reproduces this validation arithmetic, without opening export confirmation dialogs.

For total value at or below $1, zero/zero can pass the tolerance. Zero or negative bounty rates also interact badly with branches that skip division.

**Fix:** Validate money scale, non-negative tenders, supported rates, bounds and settlement conservation in one domain operation. Derive any allowed rounding residual from cent allocation, not an unconditional $1 allowance.

#### F07 — P1: Recalculating the summary erases the entered payment split [R]

[TradePanel.java:1861](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/TradePanel.java#L1861) calls PaymentTypePanel.setTotal(), which calls updatePartialSplit() when partial payment is selected. [PaymentTypePanel.java:214](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/trade/PaymentTypePanel.java#L214) resets both fields to zero.

**Fix:** Keep user-entered payment state separate from quote suggestions. Preserve it while recalculating; if a card/rate change invalidates it, show that state and require correction.

#### F08 — P1: Crash recovery loses essential card and payment state [R, S]

[TradeSessionService.java:32](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/TradeSessionService.java#L32) stores only display code/name, condition, quantity, price and two names. Payment method, split amounts, check number, identification field, raw printing identity, rarity, base price and applied rate version are absent.

[TradePanel.java:2522](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/TradePanel.java#L2522) restores every line as a nonfoil common card. Reproduced: TST 1e becomes collector number 1e with normal finish; the display name “Audit Card (Etched)” no longer matches the “Audit Card” bounty; an $8 LP row cannot recover its original $10 NM base. PLST display codes are also split incorrectly.

**Fix:** Serialize a versioned typed draft rather than table strings. Preserve exact identity and valuation inputs. Migrate recoverable old data; visibly mark information that cannot be recovered instead of inventing common/nonfoil defaults.

#### F09 — P1: Duplicating a card changes finish and can apply condition twice [R]

[TradePanel.java:1661](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/TradePanel.java#L1661) uses the two-argument TradeItem constructor, losing E/S finish, and stores the already adjusted table price as the new base. [TradePanel.java:1757](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/TradePanel.java#L1757) later applies the condition again.

Reproduced: etched becomes F internally while its display still says etched; an original $10 card at LP is $8, while its duplicate becomes $6.50 when LP pricing is reapplied.

**Fix:** Duplicate the typed line with a new line ID, preserving finish, immutable NM base, override metadata, quantity and condition.

#### F10 — P1: Card identity normalization merges different printings [R, S]

[ScryfallCatalogService.java:125](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/ScryfallCatalogService.java#L125) strips collector markers. The harness indexes 73 and 73★ and gets one key; whichever card is processed last wins. API parsing also removes punctuation at [ScryfallApiService.java:200](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/ScryfallApiService.java#L200).

Both parsers replace PLST set/collector identity with the original set identity. That may be an intended POS mapping, but using it as the domain identity prevents reliable re-fetching, recovery and links.

**Fix:** Preserve provider IDs and raw collector values. Treat any mapping that merges variants as an explicit POS policy with collision detection.

#### F11 — P1: Catalog lookups expose globally shared mutable cards [R]

[ScryfallCatalogService.java:113](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/ScryfallCatalogService.java#L113) returns the Card stored in its index. An unmodifiable map does not make its values immutable. Manual-entry paths mutate prices on those objects, e.g. [TradePanel.java:1213](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/TradePanel.java#L1213).

**Fix:** Make catalog records immutable; keep negotiated/manual price overrides on individual trade lines. Repeated lookup must never inherit an earlier customer’s manual override.

#### F12 — P1: Locale-dependent text formatting can inflate amounts 100-fold [R]

Trade tables use default-locale String.format(), then remove commas and parse the result as a decimal, e.g. [TradePanel.java:1808](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/TradePanel.java#L1808). Under a comma-decimal locale, $12,50 is interpreted as 1250. CSV formatting can introduce extra delimiter commas; history parsing has the same assumption. PosMoneyField also formats with the default locale but parses BigDecimal using a dot.

**Fix:** Keep BigDecimal values in the model. Localize display only. Use a locale-independent decimal representation in persisted and exported data and a matching locale-aware UI parser.

### Persistence, catalog and synchronization

#### F13 — P1: Configuration/autosave writes are neither atomic nor reliably reported [S]

[BuyRateService.java:283](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/BuyRateService.java#L283) writes directly to the live JSON file, catches errors, and allows callers to report successful saving and advance the generation. [TradeSessionService.java:74](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/TradeSessionService.java#L74) also overwrites its sole recovery file and suppresses errors. PrintWriter-based export paths never check checkError(), so failures after opening a file may not propagate.

**Fix:** Validate, write a temporary file, flush/close, replace safely, retain a recoverable previous version and report errors. Use writers that propagate failures. Persist successfully before announcing success or publishing new configuration.

#### F14 — P1: The preferences-to-JSON upgrade omits local rate migration [R, S]

Current [BuyRateService.java:138](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/BuyRateService.java#L138) loads only shared/local JSON. The preceding implementation used preference keys buy.rate.rules and buy.rate.bounties; this was confirmed by inspecting the parent of commit 38ccebf.

The harness seeded the legacy preferences with 90%/70% rates and a bounty. With no JSON file, 4.1.6 loaded 50%/40% defaults and no bounty.

**Fix:** When the new schema is absent, read and validate the legacy values, persist the converted configuration, verify it and record migration completion. Do not overwrite valid new configuration or delete legacy recovery material prematurely.

#### F15 — P1: Shared rate synchronization can overwrite newer work [S, Risk]

[BuyRateService.java:66](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/BuyRateService.java#L66) tracks only each instance’s last-seen file mtime. A new instance starts at zero, so an existing shared file can overwrite local changes made offline. saveRules()/saveBounties() writes both collections from that instance; a stale PreferencesPanel instance can therefore overwrite changes to the other collection.

Concurrent machines write the same JSON directly. The static generation counter does not provide cross-process concurrency control.

**Fix:** Use versioned configuration snapshots, an explicit shared/local authority policy, conflict detection and atomic publication. Reload/check the revision when saving; do not silently apply last-writer-wins to independent edits.

#### F16 — P1: Catalog replacement can discard a good cache or race another refresh [S, Risk]

[ScryfallCatalogService.java:236](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/ScryfallCatalogService.java#L236) uses one fixed temporary filename with no operation guard. Startup and Preferences can both refresh. At [ScryfallCatalogService.java:300](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/ScryfallCatalogService.java#L300), the good cache is deleted before rename; that is not an atomic replacement. Malformed lines are silently skipped and an empty/partial index may be published as successful. PrintWriter errors are also unchecked.

**Fix:** Allow one refresh operation, use a unique temporary path, validate record counts and required metadata, surface parse/write failures and atomically replace a validated cache. Keep the last good snapshot usable.

#### F17 — P2: A stale cache is not loaded when automatic refresh fails [S]

At [MainSwingApplication.java:448](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/MainSwingApplication.java#L448), a cache older than two days triggers download instead of loading the existing cache. Failure updates labels but does not fall back to loadFromDisk().

**Fix:** Load the last good cache first, expose its age, and refresh in the background. Network failure should preserve offline lookup with an explicit freshness status.

#### F18 — P2: CSV handling corrupts names and is inconsistent across outputs [R, S]

[BuyRateService.java:244](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/BuyRateService.java#L244) splits on commas instead of parsing quoted fields. A valid quoted bounty name with a comma fails. [PreferencesPanel.java:781](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/PreferencesPanel.java#L781) exports commas as semicolons, changing the name used for bounty matching.

[TradeReceivingExportService.java:364](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/TradeReceivingExportService.java#L364) replaces commas with ɕ and applies quote escaping twice to POS descriptions. CardEntry only quotes some comma-containing fields, overlooking quote/newline cases. The invoice footer at [CsvExportService.java:169](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/CsvExportService.java#L169) has seven fields under a six-field header.

**Fix:** Use one CSV reader/writer and schema-specific encoders. Preserve names exactly; only apply a documented POS-specific transform at that boundary. Cover commas, quotes, newlines, Unicode and empty fields.

#### F19 — P1: A selected export format produces different schemas in individual and combined files [R]

[CsvExportService.java:81](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/CsvExportService.java#L81) sends every format except IMPORT_UTILITY to toItemWizardRow(). [BulkPricerPanel.java:589](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/BulkPricerPanel.java#L589) separately handles ITEM_WIZARD_CHANGE_QTY_ZERO. Reproduced: per-set output has nine price columns while combined output has five quantity columns. TRADE is also exposed in the selector without a dedicated implementation.

**Fix:** Route every advertised format through one exhaustive encoder registry. Remove unsupported choices until implemented. The same rows and format must produce equivalent per-set and combined records.

#### F20 — P1: Finish support stops at several export and inventory boundaries [R, S]

[CsvExportService.java:110](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/CsvExportService.java#L110) and [BulkPricerPanel.java:606](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/BulkPricerPanel.java#L606) emit only normal/foil entries; etched-only cards disappear. [TradeReceivingExportService.java:152](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/TradeReceivingExportService.java#L152) and its inventory export turn every special finish into F. InventoryPanel only provides normal/foil quantity columns.

**Fix:** Model Finish explicitly end to end and generate valid variants using provider finish availability, not merely price presence. Represent an unavailable price separately from a nonexistent finish. Verify which finishes the POS can distinguish.

### User workflow and smaller correctness defects

#### F21 — P2: Quick price check misclassifies names and quotes a different offer [R, S]

[PriceCheckDialog.java:235](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/dialog/PriceCheckDialog.java#L235) treats any non-null CardCodeParser result as a code. “Black Lotus” is treated as a set/collector code (its trailing s is even interpreted as a surge finish), so the fuzzy-name branch is bypassed and failure has no name fallback.

At [PriceCheckDialog.java:332](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/dialog/PriceCheckDialog.java#L332), offers use the raw price; TradePanel applies pricing rules first. Reproduced: $20.26 yields a $10.13 credit quote, then $10.00 when added to the trade.

**Fix:** Give the parser an explicit success/ambiguity/failure result with a validated code grammar. Use the same quote operation in quick check and trade entry, including finish and condition.

#### F22 — P2: Search and import workers can apply obsolete results [S, Risk]

[CardSearchDialog.java:264](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/dialog/CardSearchDialog.java#L264) has no request-generation check or cancellation of superseded searches. Trade preview checks set/number but excludes finish at [TradePanel.java:993](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/TradePanel.java#L993); an old normal result can overwrite a foil request. Its old failure also unconditionally clears the preview.

The add worker at [TradePanel.java:1051](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/TradePanel.java#L1051) has no draft-generation guard. PasteImportDialog does not retain its worker for cancellation when the dialog closes, so completion can still add cards afterward.

**Fix:** Snapshot input on the EDT, assign request/draft generations, cancel superseded tasks and ignore stale completions. Bind worker lifecycle to its screen and draft.

#### F23 — P2: Network/disk I/O still runs on Swing’s event dispatch thread [S]

[TradePanel.java:1786](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/TradePanel.java#L1786) polls the shared folder during summary refresh. [FileManagerPanel.java:669](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/FileManagerPanel.java#L669) reads uncached receipts during each search-filter update; preview reads synchronously too. Autosave and local exports also run directly from UI callbacks.

**Fix:** Keep pure calculations and UI updates on the EDT; move storage/network operations to bounded workers. Preload/index history, debounce filtering and publish results by generation. Batch imports should append a collection and recalculate once, rather than refreshing all rows for each added card.

#### F24 — P1: Selecting a history row can open or print another receipt [R]

[FileManagerPanel.java:731](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/FileManagerPanel.java#L731) maps a selection back to allRecords by customer and a minute-resolution formatted date. Reproduced: selecting the second receipt for the same customer within one minute resolves to the first. Payment filters do not fix this lookup.

**Fix:** Store the TradeRecord or unique trade ID in the table model and operate directly on that selected record.

#### F25 — P1: Timestamp filenames are collision-prone transaction identities [S, Risk]

[TradeReceivingExportService.java:129](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/TradeReceivingExportService.java#L129) and [TradeReceivingExportService.java:238](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/TradeReceivingExportService.java#L238) use second-resolution timestamps and sanitized names. FileWriter truncates an existing file. Shared copies use REPLACE_EXISTING and history deduplicates by filename.

**Fix:** Assign UUIDs to transactions and include them in output identity. Use create-new semantics, idempotent exports and explicit conflict handling. Timestamps and names should be presentation metadata.

#### F26 — P2: Repeated bulk runs leave stale output and mix generations [S, Risk]

[BulkPricerPanel.java:574](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/BulkPricerPanel.java#L574) writes 00_combined_list.csv, 01_..., etc. A later smaller run overwrites the first files but leaves surplus files from the earlier run. Individual set files also overwrite the latest output without a run manifest.

**Fix:** Write each generation into a unique run directory with a manifest, schema/version, selected sets, freshness, row counts and completion status. Present one completed generation as the import set.

#### F27 — P2: Bulk completion can report success after output failure [S]

[BulkPricerPanel.java:650](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/BulkPricerPanel.java#L650) never calls get() on the worker. Exceptions from combined-file generation therefore do not reach its completion report. Cancelled work is also labeled Complete, and API/output work has limited mid-operation cancellation checks.

**Fix:** Handle success, partial success, cancellation and failure explicitly; observe worker exceptions and check cancellation at operation boundaries.

#### F28 — P2: Inventory editing has incomplete state protection [S, Risk]

[InventoryPanel.java:288](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/panel/InventoryPanel.java#L288) clears existing quantities before a new set has loaded. The selector remains changeable, while export uses its current selection for the filename, even if the table holds a different set.

isSafeToUnload() only checks whether loadedCards is empty, so an in-flight load can be treated as safe to discard. [MainSwingApplication.java:90](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/MainSwingApplication.java#L90) checks only trade work when exiting.

The inventory exporter deliberately sends zero quantities for all priced variants; this is a snapshot behavior that can reset inventory for untouched rows.

**Fix:** Track loadedSet and dirty/loading state separately, preserve work across failed loads, and protect exit/reload. Make inventory snapshot versus changes-only behavior explicit before POS acceptance testing.

#### F29 — P2: API and catalog mappers disagree and custom code conversion is reversed [R, S]

[ScryfallApiService.java:216](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/ScryfallApiService.java#L216) requires artist while catalog parsing accepts its absence and never stores artist. The probe confirms both parser divergence and metadata loss. Which real card layouts expose optional top-level fields needs a live contract fixture.

At [ScryfallApiService.java:210](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/ScryfallApiService.java#L210), getApiCode() maps in the incoming direction instead of fromScryfallCode(). Reproduced: CHK is emitted where the existing mapping declares COK. The reverse mapping is defined but unused.

**Fix:** Use one provider mapper for API/cache paths, handle optional/card-face fields deliberately and preserve metadata in the cache schema. Keep provider codes internally and perform configurable POS conversion at export.

#### F30 — P2: Domain validation and small identity helpers are inconsistent [R, S]

[BountyCard.java:33](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/model/BountyCard.java#L33) accepts negative and over-100% rates; UI and CSV paths only validate numeric syntax. BuyRateRule validates rates but not negative/duplicate thresholds. Its “always matches” catch-all comment disagrees with a strict greater-than test at zero.

[CardEntry.java:113](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/model/CardEntry.java#L113) checks uppercase F after creating lowercase f. [CardCodeParser.java:40](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/util/CardCodeParser.java#L40) rejects one-token input before reaching its compact-code branch. Reproduced: CHR143B returns null.

**Fix:** Validate invariants in constructors/factories and at persistence boundaries. Use enums and typed parsers rather than suffix inference; add boundary fixtures for zero and exact thresholds.

#### F31 — P2: Network behavior is scattered across clients and workers [S, Risk]

[ScryfallApiService.java:115](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/ScryfallApiService.java#L115) performs synchronous HTTP with per-call backoff, but different screens create their own clients and there is no shared limiter. Search throttling covers pagination, not concurrent first-page calls.

[CardImagePopup.java:89](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/CardImagePopup.java#L89) uses ImageIO.read(URL) without explicit connection/read timeouts. Its catch block also interrupts the EDT for ExecutionException, which is not a thread interruption.

**Fix:** Centralize provider requests, headers, limits, timeout/retry policy and cancellation. Bound image tasks and response sizes. Verify current Scryfall policy before choosing rate settings; the audit’s attempts to read its documentation were blocked, so no precise current quota is asserted here.

#### F32 — P2: Condition pricing can violate the rarity floor, including for NM [R, S]

[PricingService.java:78](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/PricingService.java#L78) rounds condition-adjusted values without reapplying a minimum. Reproduced: the $0.10 common floor becomes $0.00 when applying NM. The special $9.50-to-$10 rule is also absent from the general rounding documentation.

**Fix:** Define whether floors apply before/after condition and whether zero-valued cards are allowed. Preserve NM identity and monotonic condition behavior under the chosen policy; implement one well-specified calculation pipeline.

#### F33 — P2: Update comparison fails on supported suffix-style versions [R]

[UpdateCheckService.java:107](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/UpdateCheckService.java#L107) parses every component as an integer. Reproduced: 4.2.0 is not recognized as newer than 4.1.6-1. Build scripts explicitly accept and strip suffixes for packaging.

**Fix:** Use one version model for application, tag, package and update comparison; define prerelease ordering and reject malformed versions visibly in the release build.

#### F34 — P2: macOS build has path and icon defects [S; execution pending]

[build.sh:35](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/build.sh#L35) flattens source paths to a whitespace-separated string and expands it unquoted, breaking checkouts whose path contains spaces. At lines 91–92, files named for 512-pixel icon slots are generated at 400 pixels.

**Fix:** Use a build tool/source argfile that preserves paths; provide correctly sized icon assets and run actual macOS packaging checks. macOS execution was not available in this audit.

#### F35 — P2: Build and release checks do not catch behavioral regressions [S]

[release.yml:3](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/.github/workflows/release.yml#L3) runs only for pushed version tags and publishes immediately after building. There is no PR test job, executable test suite or standard dependency build descriptor. Libraries are vendored as JARs. Compiler selection and JDK tool discovery are inconsistent in the scripts; javac does not pin --release.

**Fix:** Introduce a reproducible build with pinned toolchain/dependencies, tests before publishing, artifact inspection and supported-platform packaging checks. Audit dependency provenance and advisories; library age alone is not evidence of a vulnerability.

#### F36 — P3: Documentation, UI text and version metadata contradict behavior [S]

- [build.ps1:4](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/build.ps1#L4) and [build.sh:7](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/build.sh#L7) default to 2.0.7; AppVersion is 4.1.6.
- About still calls trade management and card search “coming soon” at [MainSwingApplication.java:527](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/gui/MainSwingApplication.java#L527).
- Check labels/constants refer to 33%/33.33% while default buy rates are 40%.
- [changelog.txt:35](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/changelog.txt#L35) claims the POS COST column is correct; F03 disproves that claim.
- The changelog mixes version headings, contains “File Mangager,” and gives performance percentages with no checked-in benchmark.
- Split field Javadocs promise suggestions/equal splits, but the fields reset to zero.
- Catalog size estimates are hardcoded; actual download size was not measured here.
- HelpDialog promises follow-up contact without collecting a reply address.
- There is no root README describing build, storage, recovery or supported POS format.

**Fix:** Generate version/rate labels from actual state, document verified behavior and remove unsupported claims. Add a concise operator guide and contributor guide.

#### F37 — P2: Legacy data-folder migration is incomplete and non-retryable [S, Risk]

[AppDataDirectory.java:82](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/util/AppDataDirectory.java#L82) migrates only if the entire new root is empty, copies only direct files in immediate subdirectories, and swallows errors. A partial migration creates content that prevents a retry. Root-level legacy files and deeper subdirectories are omitted.

**Fix:** Use a versioned migration manifest, recursive verified copy, backup and resumable per-file progress. Test fresh, partial and interrupted migration without modifying originals.

#### F38 — P2: Receipt output loses information and conflates save with open [S]

[ReceiptPrintService.java:198](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/ReceiptPrintService.java#L198) writes PDF text as ISO-8859-1 while declaring WinAnsiEncoding. Characters outside Latin-1, including some names, become replacements. [TradeReceivingExportService.java:321](https://github.com/NinjaPanda351/OCC_PRICER/blob/dcf68e64ef8a845f63ae4429360dc52b1cfb8c21/src/com/cardpricer/service/TradeReceivingExportService.java#L321) truncates long card names and omits printing codes, so receipts cannot serve as complete structured trade records.

saveAsPdf() reports “PDF save failed” if opening an already-written PDF fails.

**Fix:** Generate receipts from structured transactions, include unambiguous line identity, use Unicode-capable PDF text and separate save status from open/print status.

## Verification still needed

1. Obtain a real POS product/version and accepted sample for each import mode. Confirm legacy set aliases, finish codes, taxes, quantity meaning and cost allocation.
2. Run a packaged Windows smoke test and macOS build, including paths with spaces and non-ASCII names.
3. Capture real Scryfall fixtures for distinct collector markers, PLST, double-faced cards, missing fields and current bulk formats.
4. Exercise disk-full, denied-write, concurrent refresh, disconnected share, stale configuration and interrupted migration scenarios.
5. Measure catalog peak memory and large-import responsiveness under the packaged 512 MB heap; streaming input does not by itself bound the in-memory index.
6. Review released package contents and rotate the embedded credential without attempting to authenticate with it.

Use [REPAIR_PLAN.md](REPAIR_PLAN.md) as the implementation backlog. These findings describe the audited baseline; they are not claims that fixes have already been applied.
