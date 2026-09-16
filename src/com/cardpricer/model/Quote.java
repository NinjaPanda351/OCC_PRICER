package com.cardpricer.model;
import java.time.Instant;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Immutable pricing inputs with the exact local rule revision used to approve them. */
public record Quote(Instant quotedAt, String rateRevision, List<TradeLine> lines) {
    public Quote { lines=List.copyOf(lines); }
    public JSONObject toJson() {
        return new JSONObject().put("quotedAt",quotedAt.toString()).put("rateRevision",rateRevision)
                .put("lines",new JSONArray(lines.stream().map(TradeLine::toJson).toList()));
    }
}
