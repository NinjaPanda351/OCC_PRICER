package com.cardpricer.model;

import com.cardpricer.service.ProviderCardMapper;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.UUID;

/** Immutable line snapshot; the provider payload and NM base never come from display labels. */
public record TradeLine(UUID id, PrintingIdentity printing, String cardJson, Finish finish,
                        Condition condition, int quantity, BigDecimal base, BigDecimal valuation,
                        BigDecimal creditRate, BigDecimal checkRate, String overrideCondition, BigDecimal overrideValue) {
    public TradeLine(UUID id,PrintingIdentity printing,String cardJson,Finish finish,Condition condition,int quantity,
                     BigDecimal base,BigDecimal valuation,BigDecimal creditRate,BigDecimal checkRate) {
        this(id,printing,cardJson,finish,condition,quantity,base,valuation,creditRate,checkRate,null,null);
    }
    public TradeLine {
        java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(finish); java.util.Objects.requireNonNull(condition);
        if (!printing.equals(ProviderCardMapper.fromJson(new JSONObject(cardJson)).identity()))
            throw new IllegalArgumentException("Printing identity does not match the saved provider record");
        if (quantity < 1 || base.signum() < 0 || valuation.signum() < 0)
            throw new IllegalArgumentException("Invalid trade line");
        new BuyRateRule(BigDecimal.ZERO, creditRate, checkRate);
    }
    public Card card() { return ProviderCardMapper.fromJson(new JSONObject(cardJson)); }
    public TradeItem item() {
        TradeItem item = new TradeItem(card(), finish != Finish.NORMAL, quantity, finish.code());
        item.setUnitPrice(base);
        item.setLineId(id);
        item.setManualOverride(overrideCondition,overrideValue);
        return item;
    }
    public JSONObject toJson() {
        return new JSONObject().put("id", id).put("card", new JSONObject(cardJson)).put("finish", finish)
                .put("condition", condition).put("quantity", quantity).put("base", base.toPlainString())
                .put("valuation", valuation.toPlainString()).put("creditRate", creditRate.toPlainString())
                .put("checkRate", checkRate.toPlainString()).put("overrideCondition",overrideCondition)
                .put("overrideValue",overrideValue==null ? null : overrideValue.toPlainString());
    }
    public static TradeLine fromJson(JSONObject json) {
        Card card = ProviderCardMapper.fromJson(json.getJSONObject("card"));
        return new TradeLine(UUID.fromString(json.getString("id")), card.identity(), json.getJSONObject("card").toString(),
                Finish.valueOf(json.getString("finish")), Condition.valueOf(json.getString("condition")), json.getInt("quantity"),
                json.getBigDecimal("base"), json.getBigDecimal("valuation"), json.getBigDecimal("creditRate"), json.getBigDecimal("checkRate"),
                json.optString("overrideCondition",null),json.has("overrideValue") ? json.getBigDecimal("overrideValue") : null);
    }
}
