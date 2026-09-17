# Provisional receiving CSV contract

The audit proved a header/row mismatch. This implementation fixes that internal contract; no POS vendor/version or accepted external fixture was supplied. Confirm all modes and mappings with the actual POS before rollout.

UTF-8, RFC 4180 escaping, dot-decimal money, CRLF row endings. Every receiving row has 19 fields:

The receiving POS requires commas in card names to be replaced with the literal character `ɕ` (U+0255, decimal entity `&#597;`). A name containing commas alone is therefore exported without quotation marks. This is an export-only substitution; the saved trade and receipt retain the original name. Other characters requiring CSV escaping, such as literal quotes or newlines, still use the CSV encoder.

| Position | Field | Value |
|---|---|---|
| 1 | LINE NO | Sequential exported row number |
| 2 | DEPARTMENT | Existing value `5` |
| 3 | CATEGORY | Existing value `5.2` |
| 4 | TYPE | Empty |
| 5 | CODE | POS set alias, exact collector identifier and finish suffix |
| 6 | ITEM TYPE | Empty |
| 7 | ORDER NO | Empty |
| 8 | DESCRIPTION | Full name and finish |
| 9 | UOM | Empty |
| 10 | QTY ON ORD | Acquired quantity |
| 11 | RESTOCK LEVEL | Empty |
| 12 | REORDER POINT | Empty |
| 13 | QTY ON HAND | Empty |
| 14 | COST | Approved unit acquisition cost |
| 15 | DISCOUNT | Empty |
| 16 | BID | Empty |
| 17 | EXTENDED COST | Quantity times approved unit cost |
| 18 | TAX CODE | Existing value `TAX` |
| 19 | PRICE | Condition-adjusted valuation |

The sum of extended costs equals the settlement allocation for included stock lines. MISC remains in the settlement and receipt but has no stock row. When a line's exact acquisition cost is not divisible into identical cent-denominated unit costs, it produces two quantity rows whose combined cost and quantity reconcile exactly.

Provider identity stays unchanged internally. Existing COK/XED aliases map only at export. Finishes remain distinct (`F`, `E`, `S`); the adapter rejects collisions between different provider identities. Confirm whether the POS accepts collector markers, PLST identifiers and distinct finish suffixes. Do not silently collapse these to generic foil.

Other supported schemas remain Import Utility (8 columns), Item Wizard (9 columns), and Item Wizard Change Qty (5 columns). The zero-quantity mode is the same for combined and individual exports. Inventory Change Qty is a snapshot, with blank old quantity and explicit new quantity; zero can reset stock.

Unresolved external acceptance: actual tax/category meanings, condition representation, per-unit versus extended acquisition cost semantics, duplicate SKU rows for cent allocation, quantity update semantics and all finish/legacy aliases.
