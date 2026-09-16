package com.cardpricer.model;

import org.json.JSONObject;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

/** Immutable checkbox operation. Logical ordering is independent of workstation clock skew. */
public record InventoryStatusChange(UUID id, String recordKey, long revision, long sequence,
                                    boolean inventoried, Instant updatedAt) {
    public InventoryStatusChange {
        java.util.Objects.requireNonNull(id);
        java.util.Objects.requireNonNull(updatedAt);
        if (recordKey == null || !(recordKey.startsWith("trade:") || recordKey.startsWith("receipt:"))
                || recordKey.length() > 1024 || revision < 0 || sequence < 1)
            throw new IllegalArgumentException("Invalid POS inventory status change");
    }

    // A newer trade revision always wins. Concurrent opposite changes favor review (unchecked).
    public static final Comparator<InventoryStatusChange> ORDER = Comparator
            .comparingLong(InventoryStatusChange::revision)
            .thenComparingLong(InventoryStatusChange::sequence)
            .thenComparing(change -> !change.inventoried())
            .thenComparing(change -> change.id().toString());

    public JSONObject toJson() {
        return new JSONObject().put("schema",1).put("id",id.toString()).put("recordKey",recordKey)
                .put("revision",revision).put("sequence",sequence).put("inventoried",inventoried)
                .put("updatedAt",updatedAt.toString());
    }

    public static InventoryStatusChange fromJson(JSONObject json) {
        if (json.getInt("schema") != 1) throw new IllegalArgumentException("Unsupported POS status schema");
        return new InventoryStatusChange(UUID.fromString(json.getString("id")),json.getString("recordKey"),
                json.getLong("revision"),json.getLong("sequence"),json.getBoolean("inventoried"),
                Instant.parse(json.getString("updatedAt")));
    }
}
