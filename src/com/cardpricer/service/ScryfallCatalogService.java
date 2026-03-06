package com.cardpricer.service;

import com.cardpricer.model.Card;
import com.cardpricer.util.AppDataDirectory;
import com.cardpricer.util.VintageUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Singleton service that maintains a local catalog of Scryfall cards for
 * instant lookup without per-card API calls.
 *
 * <p>The catalog is built from Scryfall's {@code default_cards} bulk-data file
 * (every English printing). After the first download the data is saved as a
 * compact NDJSON.gz file (~15–25 MB) in the application cache directory and
 * reloaded on subsequent launches without another network call.
 *
 * <p><b>Index key format:</b> {@code "SETCODE:COLLNUM"} — both components are
 * upper-case; special characters (★) are stripped from the collector number;
 * hyphens are preserved (required for PLST composites, e.g. {@code "PLST:ARB-1"}).
 * Vintage set aliases (e.g. {@code "alpha"} → {@code "lea"}) are resolved via
 * {@link VintageUtil#resolveSetAlias(String)} so that user input always maps to
 * the canonical Scryfall set code.
 */
public class ScryfallCatalogService {

    // ── Public interface ──────────────────────────────────────────────────────

    /**
     * Progress callback for long-running download/build operations.
     * All methods are called on the background thread — implementations must
     * dispatch EDT updates via {@code SwingUtilities.invokeLater}.
     */
    public interface DownloadProgress {
        /**
         * Called periodically during download and build.
         *
         * @param cardsProcessed number of cards indexed so far
         * @param phase          human-readable description of the current phase
         */
        void onUpdate(int cardsProcessed, String phase);

        /** Returns {@code true} if the user has requested cancellation. */
        boolean isCancelled();
    }

    // ── Singleton ────────────────────────────────────────────────────────────

    private static final ScryfallCatalogService INSTANCE = new ScryfallCatalogService();

    private ScryfallCatalogService() {}

    /** Returns the singleton instance. */
    public static ScryfallCatalogService getInstance() { return INSTANCE; }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String BULK_DATA_API  = "https://api.scryfall.com/bulk-data";
    private static final String USER_AGENT     = "CardPricerApp/1.0";
    private static final String CACHE_FILENAME = "catalog.ndjson.gz";

    // ── State ─────────────────────────────────────────────────────────────────

    /** Populated after a successful load or build; {@code null} when not loaded. */
    private volatile Map<String, Card> index;
    private volatile int cardCount;

    // ── Public accessors ──────────────────────────────────────────────────────

    /** Returns {@code true} if the local NDJSON.gz cache file exists on disk. */
    public boolean isCatalogAvailable() { return getCacheFile().exists(); }

    /**
     * Returns the age of the cache file in milliseconds,
     * or {@link Long#MAX_VALUE} if the cache does not exist.
     */
    public long getCacheAgeMs() {
        File f = getCacheFile();
        return f.exists() ? System.currentTimeMillis() - f.lastModified() : Long.MAX_VALUE;
    }

    /** Returns the number of cards in the currently loaded index, or 0 if not loaded. */
    public int getCardCount() { return cardCount; }

    /** Returns {@code true} if the index is loaded into memory and ready for lookup. */
    public boolean isLoaded() { return index != null; }

    /** Clears the in-memory index without deleting the cache file. */
    public void invalidate() {
        index = null;
        cardCount = 0;
    }

    /**
     * Looks up a card by set code and collector number.
     *
     * <p>The set code is resolved through the vintage alias table before the
     * lookup, so {@code "alpha"} and {@code "lea"} both find Alpha cards.
     *
     * @param setCode         Scryfall or user-facing set code (any case)
     * @param collectorNumber collector number, possibly with finish markers (e.g. {@code "73★"})
     * @return the matching {@link Card}, or {@link Optional#empty()} if not in the catalog
     */
    public Optional<Card> lookup(String setCode, String collectorNumber) {
        Map<String, Card> snapshot = index;
        if (snapshot == null) return Optional.empty();
        return Optional.ofNullable(snapshot.get(buildKey(setCode, collectorNumber)));
    }

    // ── Key normalisation ─────────────────────────────────────────────────────

    /**
     * Builds a lookup key: {@code "SETCODE:COLLNUM"} — both upper-case;
     * finish markers and other special chars stripped; hyphens preserved.
     */
    private static String buildKey(String setCode, String collectorNumber) {
        // Resolve vintage aliases ("alpha" → "lea") to match how fetchCard() works
        String resolved  = VintageUtil.resolveSetAlias(setCode);
        // Strip non-alphanumeric except hyphens (removes ★ for surge foil)
        String cleanColl = collectorNumber.replaceAll("[^0-9A-Za-z\\-]", "").toUpperCase();
        return resolved.toUpperCase() + ":" + cleanColl;
    }

    // ── Cache file path ───────────────────────────────────────────────────────

    private static File getCacheFile() {
        return new File(AppDataDirectory.cache(), CACHE_FILENAME);
    }

    // ── Load from local cache ─────────────────────────────────────────────────

    /**
     * Loads the catalog from the local NDJSON.gz cache into memory.
     *
     * @return the number of cards loaded
     * @throws IOException if the cache file does not exist or cannot be read
     */
    public int loadFromDisk() throws IOException {
        File cacheFile = getCacheFile();
        if (!cacheFile.exists()) {
            throw new IOException("Catalog cache not found: " + cacheFile.getAbsolutePath());
        }

        Map<String, Card> newIndex = new HashMap<>(400_000);
        int count = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new FileInputStream(cacheFile)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    JSONObject obj = new JSONObject(line);
                    Card card = cardFromCacheLine(obj);
                    newIndex.put(obj.getString("k"), card);
                    count++;
                } catch (Exception ignored) {
                    // Skip any malformed lines — they should not occur in a clean cache
                }
            }
        }

        this.index     = Collections.unmodifiableMap(newIndex);
        this.cardCount = count;
        return count;
    }

    /** Deserialises a {@link Card} from a compact cache-line JSON object. */
    private static Card cardFromCacheLine(JSONObject obj) {
        Card card = new Card();
        card.setName(obj.getString("nm"));
        card.setSetCode(obj.getString("s"));
        card.setCollectorNumber(obj.getString("n"));
        card.setRarity(obj.optString("r", "common"));

        // Prices: absent key → null → Card.setPrice(null) stores "N/A"
        card.setPrice(    obj.has("p")  ? obj.getString("p")  : null);
        card.setFoilPrice(obj.has("fp") ? obj.getString("fp") : null);
        card.setEtchedPrice(obj.has("ep") ? obj.getString("ep") : null);

        card.setReserved(obj.optBoolean("rl", false));

        if (obj.has("fx")) {
            JSONArray fxArr = obj.getJSONArray("fx");
            List<String> fx = new ArrayList<>(fxArr.length());
            for (int i = 0; i < fxArr.length(); i++) fx.add(fxArr.getString(i));
            card.setFrameEffects(fx);
        }
        if (obj.has("i")) card.setImageUrl(obj.getString("i"));
        return card;
    }

    // ── Download and build ────────────────────────────────────────────────────

    /**
     * Downloads the Scryfall {@code default_cards} bulk file, parses it while
     * streaming (no OOM risk), writes a compact NDJSON.gz cache to disk, and
     * loads the result into the in-memory index.
     *
     * <p>This method is synchronous and should be called from a background thread
     * (e.g. a {@link javax.swing.SwingWorker}).
     *
     * @param progress optional progress / cancellation callback; may be {@code null}
     * @throws InterruptedException if cancelled via the progress callback
     * @throws Exception            on any network or I/O failure
     */
    public void downloadAndBuild(DownloadProgress progress) throws Exception {
        // Step 1 — find the download URI in Scryfall's bulk-data catalogue
        if (progress != null) {
            if (progress.isCancelled()) return;
            progress.onUpdate(0, "Looking up Scryfall bulk data URL\u2026");
        }

        JSONObject bulkMeta   = fetchBulkDataMeta();
        String     downloadUrl = bulkMeta.getString("download_uri");

        if (progress != null) {
            if (progress.isCancelled()) return;
            progress.onUpdate(0, "Connecting to Scryfall\u2026");
        }

        // Step 2 — stream, parse, and write cache simultaneously
        File cacheFile = getCacheFile();
        File tmpFile   = new File(cacheFile.getParent(), CACHE_FILENAME + ".tmp");
        Map<String, Card> newIndex = new HashMap<>(400_000);

        HttpURLConnection conn = null;
        try {
            conn = openConnection(downloadUrl);

            try (InputStream       httpIn     = conn.getInputStream();
                 InputStream       streamIn   = maybeWrapGzip(httpIn);
                 BufferedReader    reader     = new BufferedReader(
                         new InputStreamReader(streamIn, StandardCharsets.UTF_8), 65_536);
                 FileOutputStream  fos        = new FileOutputStream(tmpFile);
                 GZIPOutputStream  gzOut      = new GZIPOutputStream(new BufferedOutputStream(fos));
                 PrintWriter       cacheWriter = new PrintWriter(
                         new OutputStreamWriter(gzOut, StandardCharsets.UTF_8))) {

                // The Scryfall bulk file is one giant JSON array: [{card}, {card}, …]
                // Stream it token-by-token so we never load the whole thing into memory.
                JSONTokener tokener = new JSONTokener(reader);

                char open = tokener.nextClean();
                if (open != '[') {
                    throw new IOException("Expected '[' at start of bulk data, got: '" + open + "'");
                }

                int     cardsProcessed = 0;
                boolean done           = false;

                while (!done) {
                    if (progress != null && progress.isCancelled()) {
                        tmpFile.delete();
                        throw new InterruptedException("Catalog download cancelled by user");
                    }

                    char c = tokener.nextClean();
                    switch (c) {
                        case ']' -> done = true;
                        case ',' -> { /* separator between objects */ }
                        case '{' -> {
                            tokener.back();
                            JSONObject cardJson = new JSONObject(tokener);

                            // Index English, non-digital printings only
                            if ("en".equals(cardJson.optString("lang"))
                                    && !cardJson.optBoolean("digital", false)) {
                                processCardJson(cardJson, newIndex, cacheWriter);
                                cardsProcessed++;

                                if (cardsProcessed % 5_000 == 0 && progress != null) {
                                    progress.onUpdate(cardsProcessed, "Parsing cards\u2026");
                                }
                            }
                        }
                        default -> {
                            // Unexpected character; treat end-of-stream as end of array
                            if (!tokener.more()) done = true;
                        }
                    }
                }

                cacheWriter.flush();
                if (progress != null) {
                    progress.onUpdate(cardsProcessed, "Saving catalog to disk\u2026");
                }
            }
        } finally {
            if (conn != null) conn.disconnect();
        }

        // Step 3 — atomically replace the old cache file
        if (cacheFile.exists()) cacheFile.delete();
        if (!tmpFile.renameTo(cacheFile)) {
            // Fallback for cross-device moves (e.g. temp dir on a different drive)
            try (InputStream  in  = new FileInputStream(tmpFile);
                 OutputStream out = new FileOutputStream(cacheFile)) {
                in.transferTo(out);
            }
            tmpFile.delete();
        }

        this.index     = Collections.unmodifiableMap(newIndex);
        this.cardCount = newIndex.size();

        if (progress != null) {
            progress.onUpdate(this.cardCount,
                    "Done \u2014 " + this.cardCount + " cards indexed.");
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Calls the Scryfall bulk-data API and returns the metadata object for
     * the {@code default_cards} type.
     */
    private JSONObject fetchBulkDataMeta() throws Exception {
        HttpURLConnection conn = openConnection(BULK_DATA_API);
        try {
            if (conn.getResponseCode() != 200) {
                throw new IOException("Scryfall bulk-data API returned HTTP "
                        + conn.getResponseCode());
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONObject response = new JSONObject(sb.toString());
            JSONArray  items    = response.getJSONArray("data");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                if ("default_cards".equals(item.optString("type"))) return item;
            }
            throw new IOException("'default_cards' type not found in Scryfall bulk-data response");
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Peeks at the first two bytes of the stream to detect the gzip magic number
     * (0x1f 0x8b) and wraps with {@link GZIPInputStream} if present.
     * Returns the stream unchanged if the content is not gzip-compressed.
     * This handles Scryfall serving bulk files as plain JSON vs. gzip transparently.
     */
    private static InputStream maybeWrapGzip(InputStream in) throws IOException {
        PushbackInputStream pb = new PushbackInputStream(in, 2);
        byte[] magic = new byte[2];
        int read = pb.read(magic, 0, 2);
        if (read > 0) pb.unread(magic, 0, read);
        if (read == 2 && magic[0] == (byte) 0x1f && magic[1] == (byte) 0x8b) {
            return new GZIPInputStream(pb, 65_536);
        }
        return pb;
    }

    private static HttpURLConnection openConnection(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(180_000); // bulk file is large — allow up to 3 min
        return conn;
    }

    /**
     * Parses one card JSON object from the Scryfall bulk array, adds it to the
     * in-memory index, and appends a compact line to the NDJSON.gz cache writer.
     *
     * <p>PLST cards are stored with the <em>source</em> set code and collector
     * number (e.g. setCode="ARB", collNum="1") to match the behaviour of
     * {@link ScryfallApiService#parseCardFromJson}, while the index key uses the
     * full PLST composite number (e.g. {@code "PLST:ARB-1"}).
     */
    private static void processCardJson(JSONObject json,
                                        Map<String, Card> index,
                                        PrintWriter cacheWriter) {
        try {
            String rawSet  = json.getString("set");               // "tdm", "plst", "lea"
            String rawColl = json.getString("collector_number");  // "3", "ARB-1", "73★"
            String name    = json.getString("name");
            String rarity  = json.optString("rarity", "common");
            boolean reserved = json.optBoolean("reserved", false);

            // ── Set code and collector number normalisation ────────────────────
            String setCode; // stored in Card and cache
            String collNum; // stored in Card and cache
            String key;     // index key

            if ("plst".equals(rawSet)) {
                // PLST composite collector numbers are "SET-NUM", e.g. "ARB-1"
                int hyphen = rawColl.lastIndexOf('-');
                if (hyphen <= 0) return; // malformed — skip
                setCode = rawColl.substring(0, hyphen).toUpperCase(); // "ARB"
                collNum = rawColl.substring(hyphen + 1);              // "1"
                key     = "PLST:" + rawColl.toUpperCase();            // "PLST:ARB-1"
            } else {
                setCode = rawSet.toUpperCase();                                // "TDM"
                collNum = rawColl.replaceAll("[^0-9A-Za-z\\-]", "");           // "73" (strips ★)
                key     = setCode + ":" + collNum.toUpperCase();               // "TDM:3"
            }

            // ── Prices ────────────────────────────────────────────────────────
            String normalPrice  = null;
            String foilPrice    = null;
            String etchedPrice  = null;
            if (json.has("prices")) {
                JSONObject prices = json.getJSONObject("prices");
                if (!prices.isNull("usd"))        normalPrice  = prices.getString("usd");
                if (!prices.isNull("usd_foil"))   foilPrice    = prices.getString("usd_foil");
                if (!prices.isNull("usd_etched")) etchedPrice  = prices.getString("usd_etched");
            }

            // ── Frame effects ─────────────────────────────────────────────────
            List<String> frameEffects = new ArrayList<>();
            if (json.has("frame_effects")) {
                JSONArray fxArr = json.getJSONArray("frame_effects");
                for (int i = 0; i < fxArr.length(); i++) frameEffects.add(fxArr.getString(i));
            }

            // ── Image URL ─────────────────────────────────────────────────────
            String imageUrl = null;
            if (json.has("image_uris")) {
                JSONObject iu = json.getJSONObject("image_uris");
                if (iu.has("normal")) imageUrl = iu.getString("normal");
            } else if (json.has("card_faces")) {
                JSONArray faces = json.getJSONArray("card_faces");
                if (faces.length() > 0) {
                    JSONObject front = faces.getJSONObject(0);
                    if (front.has("image_uris")) {
                        JSONObject iu = front.getJSONObject("image_uris");
                        if (iu.has("normal")) imageUrl = iu.getString("normal");
                    }
                }
            }

            // ── Build Card object ─────────────────────────────────────────────
            Card card = new Card();
            card.setName(name);
            card.setSetCode(setCode);
            card.setCollectorNumber(collNum);
            card.setRarity(rarity);
            card.setPrice(normalPrice);
            card.setFoilPrice(foilPrice);
            card.setEtchedPrice(etchedPrice);
            card.setReserved(reserved);
            if (!frameEffects.isEmpty()) card.setFrameEffects(frameEffects);
            if (imageUrl != null) card.setImageUrl(imageUrl);

            index.put(key, card);

            // ── Write compact NDJSON cache line ───────────────────────────────
            JSONObject cacheObj = new JSONObject();
            cacheObj.put("k",  key);
            cacheObj.put("nm", name);
            cacheObj.put("s",  setCode);
            cacheObj.put("n",  collNum);
            cacheObj.put("r",  rarity);
            if (normalPrice  != null) cacheObj.put("p",  normalPrice);
            if (foilPrice    != null) cacheObj.put("fp", foilPrice);
            if (etchedPrice  != null) cacheObj.put("ep", etchedPrice);
            if (reserved)             cacheObj.put("rl", true);
            if (!frameEffects.isEmpty()) {
                JSONArray fxArr = new JSONArray();
                frameEffects.forEach(fxArr::put);
                cacheObj.put("fx", fxArr);
            }
            if (imageUrl != null) cacheObj.put("i", imageUrl);
            cacheWriter.println(cacheObj);

        } catch (Exception ignored) {
            // Skip individual malformed entries without aborting the whole build
        }
    }
}
