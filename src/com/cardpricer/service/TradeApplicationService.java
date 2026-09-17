package com.cardpricer.service;

import com.cardpricer.model.TradeDraft;
import com.cardpricer.util.AppVersion;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Finalization never depends on the success of a CSV write or network share. */
public final class TradeApplicationService {
    private final TradeRepository repository;
    private final Path outputs;
    private final Path shared;
    public TradeApplicationService(TradeRepository repository, Path outputs) { this(repository,outputs,null); }
    public TradeApplicationService(TradeRepository repository, Path outputs, Path shared) {
        this.repository = repository; this.outputs = outputs; this.shared=shared;
    }
    public int finalizeTrade(TradeDraft draft) throws Exception {
        draft.validateForApproval();
        TradeDraft existing=repository.committedDraft(draft.id());
        if (existing!=null) {
            var before=existing.toJson(); var after=draft.toJson(); before.remove("revision");after.remove("revision");
            before.remove("quotedAt");after.remove("quotedAt");
            if (!before.similar(after)) throw new IllegalStateException("This trade is already approved. Open it with Edit trade in History to save corrections, or use Retry exports for pending files.");
        } else {
            repository.commit(draft, outputs(draft, false));
        }
        return repository.retryOutputs(outputs);
    }

    public int updateTrade(TradeDraft draft, long expectedRevision) throws Exception {
        draft.validateForApproval();
        if (shared!=null) return new SharedTradeService(repository,outputs,shared).update(draft,expectedRevision);
        if (!repository.sharedSource(draft.id()).isBlank())
            throw new IllegalStateException("Reconnect this trade's Shared Trades Folder before saving corrections.");
        repository.revise(draft, expectedRevision, outputs(draft, true));
        return repository.retryOutputs(outputs);
    }

    public static String outputPrefix(TradeDraft draft, boolean correction) {
        java.time.LocalDateTime date=null;
        try { date=java.time.LocalDateTime.ofInstant(java.time.Instant.parse(draft.quotedAt()),java.time.ZoneId.systemDefault()); }
        catch (java.time.format.DateTimeParseException ignored) { /* Older snapshots may not include a date. */ }
        return filenamePrefix(date,draft.customer(),draft.trader(),draft.id())
                + (correction ? "_rev" + draft.revision() : "");
    }

    static String filenamePrefix(java.time.LocalDateTime date, String customer, String employee, java.util.UUID id) {
        String timestamp=date==null ? "Undated" : date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        return timestamp + " - " + filenameName(customer,"Unknown customer")
                + " - " + filenameName(employee,"Unknown employee")
                + " - " + id.toString().replace("-", "").substring(0,12);
    }

    private static String filenameName(String name, String fallback) {
        // Retain readable Unicode names and spaces; remove Windows/path control characters.
        String safe=name==null ? "" : name.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", " ")
                .replaceAll("\\s+", " ").strip().replaceAll("^[. ]+|[. ]+$", "");
        if (safe.isBlank()) return fallback;
        if (safe.codePointCount(0,safe.length())>40) safe=safe.substring(0,safe.offsetByCodePoints(0,40));
        return safe.replaceAll("[. ]+$", "");
    }

    static List<TradeRepository.Output> outputs(TradeDraft draft, boolean correction) throws Exception {
            var settlement = draft.settle();
            List<TradeRepository.Output> jobs = new ArrayList<>();
            String prefix = outputPrefix(draft, correction);
            if (settlement.lines().stream().anyMatch(line -> line.line().stock())) {
                StringWriter csv = new StringWriter();
                TradePosEncoder.write(csv, draft.lines().stream().map(line -> line.item()).toList(),
                        draft.lines().stream().map(line -> line.valuation()).toList(), settlement);
                jobs.add(new TradeRepository.Output(prefix + ".csv", csv.toString()));
            }
            jobs.add(new TradeRepository.Output(prefix + ".json", draft.toJson().put("applicationVersion", AppVersion.CURRENT).toString(2)));
            jobs.add(new TradeRepository.Output(prefix + ".txt", receipt(draft, settlement)));
            return jobs;
    }
    public static String receipt(TradeDraft draft, SettlementEngine.Settlement settlement) {
        String rule = "----------------------------------------------------------------\n";
        String payment = switch (draft.payment()) {
            case "credit" -> "Store Credit";
            case "check" -> "Check";
            case "partial" -> "Partial (Store Credit + Check)";
            case "inventory" -> "Inventory";
            default -> draft.payment();
        };
        StringBuilder text = new StringBuilder("OCC CARD PRICER | TRADE RECEIPT\n").append(rule)
                .append("Trade ID: ").append(draft.id()).append("\nRevision: ").append(draft.revision())
                .append("\nPrepared: ").append(readableDate(draft.quotedAt()))
                .append("\n\nCUSTOMER & PAYMENT\n").append(rule)
                .append("Customer Name: ").append(draft.customer()).append("\nTrader Name: ").append(draft.trader())
                .append("\nPayment Method: ").append(payment)
                .append("\nCheck Number: ").append(draft.checkNumber().isBlank() ? "-" : draft.checkNumber())
                .append("\n\nCARDS RECEIVED\n").append(rule);
        for (int i = 0; i < draft.lines().size(); i++) {
            var line = draft.lines().get(i);
            var allocation = settlement.lines().get(i);
            text.append(i + 1).append(". ").append(line.card().getName()).append('\n')
                    .append("   Printing: ").append(line.printing().set()).append(" #").append(line.printing().collectorNumber())
                    .append(" | ").append(line.finish()).append(" | ").append(line.condition()).append('\n')
                    .append("   Quantity: ").append(line.quantity()).append(" | Market each: ").append(money(line.valuation()))
                    .append(" | Market total: ").append(money(allocation.line().market())).append('\n')
                    .append("   Payout: ").append(money(allocation.cost())).append(" (Credit ").append(money(allocation.credit()))
                    .append(" + Check ").append(money(allocation.check())).append(")\n\n");
        }
        text.append("TRADE TOTALS\n").append(rule)
                .append("Total Cards: ").append(draft.lines().stream().mapToInt(line -> line.quantity()).sum())
                .append("\nTotal Value: ").append(money(settlement.market()))
                .append("\nStore Credit Payout: ").append(money(settlement.credit()))
                .append("\nCheck Payout: ").append(money(settlement.check()))
                .append("\nTOTAL PAYOUT: ").append(money(settlement.total())).append('\n').append(rule);
        return text.toString();
    }

    private static String money(java.math.BigDecimal value) {
        return "$" + value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String readableDate(String instant) {
        try { return java.time.Instant.parse(instant).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, uuuu 'at' h:mm a z",java.util.Locale.US)); }
        catch (java.time.format.DateTimeParseException ignored) { return "Not recorded"; }
    }
}
