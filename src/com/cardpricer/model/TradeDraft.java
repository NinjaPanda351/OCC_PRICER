package com.cardpricer.model;

import com.cardpricer.service.SettlementEngine;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

public record TradeDraft(UUID id, long revision, String trader, String customer, String identification,
                         String checkNumber, String payment, BigDecimal credit, BigDecimal check,
                         List<TradeLine> lines, String quotedAt, String rateRevision) {
    public TradeDraft { lines = List.copyOf(lines); }
    public void validateForApproval() {
        if (lines.stream().anyMatch(line -> "legacy-unverified".equals(line.printing().providerId())))
            throw new IllegalArgumentException("Re-enter recovered legacy lines to verify printing, finish and NM base before finalizing.");
        settle();
    }
    public TradeDraft(UUID id,long revision,String trader,String customer,String identification,String checkNumber,
                      String payment,BigDecimal credit,BigDecimal check,List<TradeLine> lines) {
        this(id,revision,trader,customer,identification,checkNumber,payment,credit,check,lines,"unknown","unknown");
    }
    public SettlementEngine.Settlement settle() {
        return new SettlementEngine().settle(lines.stream().map(line -> {
            BigDecimal qty = BigDecimal.valueOf(line.quantity());
            return new SettlementEngine.Line(line.id().toString(), line.quantity(), line.valuation().multiply(qty),
                    line.valuation().multiply(line.creditRate()).setScale(2, RoundingMode.HALF_UP).multiply(qty),
                    line.valuation().multiply(line.checkRate()).setScale(2, RoundingMode.HALF_UP).multiply(qty),
                    !"MISC".equalsIgnoreCase(line.printing().set()));
        }).toList(), payment, credit, check);
    }
    public JSONObject toJson() {
        return new JSONObject().put("schema", 1).put("id", id).put("revision", revision).put("trader", trader)
                .put("customer", customer).put("identification", identification).put("checkNumber", checkNumber)
                .put("payment", payment).put("credit", credit.toPlainString()).put("check", check.toPlainString())
                .put("quotedAt",quotedAt).put("rateRevision",rateRevision)
                .put("lines", new JSONArray(lines.stream().map(TradeLine::toJson).toList()));
    }
    public static TradeDraft fromJson(JSONObject json) {
        if (json.getInt("schema") != 1) throw new IllegalArgumentException("Unsupported draft schema");
        var lines = new java.util.ArrayList<TradeLine>();
        for (Object value : json.getJSONArray("lines")) lines.add(TradeLine.fromJson((JSONObject)value));
        return new TradeDraft(UUID.fromString(json.getString("id")), json.getLong("revision"),
                json.getString("trader"), json.getString("customer"), json.getString("identification"),
                json.getString("checkNumber"), json.getString("payment"), json.getBigDecimal("credit"), json.getBigDecimal("check"), lines,
                json.optString("quotedAt","unknown"),json.optString("rateRevision","unknown"));
    }
}
