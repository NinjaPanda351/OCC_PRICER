package com.cardpricer.service;

import com.cardpricer.util.AppDataDirectory;
import com.cardpricer.util.AtomicFiles;
import com.cardpricer.util.SetList;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Persistent, daily-refreshed Scryfall set directory. All I/O belongs off the EDT. */
public class SetCatalogService {
    private static final String SETS_URL = "https://api.scryfall.com/sets";
    private final ScryfallApiService api;
    private final Path cache;
    private final Clock clock;

    public record Entry(String code, String apiCode, String name, String releasedAt) {
        public String label() { return name.isBlank() ? code : code + " — " + name; }
        public boolean matches(String query) {
            return (code + " " + apiCode + " " + name).toLowerCase(Locale.ROOT).contains(query);
        }
    }

    public record Catalog(List<Entry> sets, Instant updatedAt, String notice) {
        public Catalog { sets = List.copyOf(sets); }
    }

    public SetCatalogService() {
        this(new ScryfallApiService(), AppDataDirectory.root().toPath().resolve("cache/sets-v1.json"), Clock.systemUTC());
    }

    public SetCatalogService(ScryfallApiService api, Path cache, Clock clock) {
        this.api = api;
        this.cache = cache;
        this.clock = clock;
    }

    public static Catalog bundled() {
        return new Catalog(SetList.ALL_SETS_CUSTOM_CODES.stream()
                .map(code -> new Entry(code, SetList.toScryfallCode(code), "", "")).toList(),
                Instant.EPOCH, "Bundled list; connect to update");
    }

    public Catalog loadCached() {
        if (!Files.exists(cache)) return bundled();
        try {
            JSONObject saved = new JSONObject(Files.readString(cache));
            if (saved.getInt("version") != 1) throw new IOException("Unsupported set cache");
            return new Catalog(parse(saved.getJSONArray("data")), Instant.parse(saved.getString("updated_at")), "");
        } catch (Exception failure) {
            return new Catalog(bundled().sets(), Instant.EPOCH, "Saved list unavailable; using bundled list");
        }
    }

    public boolean needsRefresh(Catalog catalog) {
        return catalog.updatedAt().equals(Instant.EPOCH)
                || !catalog.updatedAt().plus(Duration.ofDays(1)).isAfter(clock.instant())
                || catalog.updatedAt().isAfter(clock.instant());
    }

    public Catalog refresh() throws Exception {
        JSONObject response = api.makeApiCall(SETS_URL);
        // /sets returns the full directory. Never replace a good cache with a partial response.
        if (!"list".equals(response.getString("object")) || response.getBoolean("has_more")) {
            throw new IOException("Incomplete set directory received");
        }
        JSONArray data = response.getJSONArray("data");
        List<Entry> entries = parse(data);
        Instant updatedAt = clock.instant();
        String notice = "";
        try {
            AtomicFiles.write(cache, new JSONObject().put("version", 1).put("updated_at", updatedAt.toString())
                    .put("data", data).toString(2));
        } catch (IOException failure) {
            notice = "Updated, but could not save list for offline use";
        }
        return new Catalog(entries, updatedAt, notice);
    }

    private static List<Entry> parse(JSONArray data) throws IOException {
        Map<String, Entry> entries = new HashMap<>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject set = data.getJSONObject(i);
            String type = set.getString("set_type");
            if (type.equals("promo") || type.equals("token")) continue;
            String apiCode = set.getString("code").toLowerCase(Locale.ROOT);
            String name = set.getString("name");
            // Scryfall groups Art Series and Front Cards with other memorabilia.
            String normalizedName = name.toLowerCase(Locale.ROOT);
            if (type.equals("memorabilia") && (normalizedName.endsWith("art series")
                    || normalizedName.endsWith("front cards"))) continue;
            if (!apiCode.matches("[a-z0-9]{2,8}") || name.isBlank() || type.isBlank()) {
                throw new IOException("Invalid set directory entry");
            }
            String date = set.optString("released_at", "");
            if (!date.isBlank()) LocalDate.parse(date);
            String code = SetList.fromScryfallCode(apiCode);
            // An API code can also be an existing POS alias for a different set
            // (jvc is Anthology, while the POS alias JVC points to dd2).
            if (!apiCode.equals(SetList.toScryfallCode(code))) code += " (Scryfall)";
            if (entries.putIfAbsent(code, new Entry(code, apiCode, name, date)) != null) {
                throw new IOException("Duplicate set code: " + code);
            }
        }
        if (entries.isEmpty()) throw new IOException("Empty set directory received");
        return entries.values().stream().sorted(Comparator.comparing(Entry::releasedAt).reversed()
                .thenComparing(Entry::name).thenComparing(Entry::code)).toList();
    }
}
