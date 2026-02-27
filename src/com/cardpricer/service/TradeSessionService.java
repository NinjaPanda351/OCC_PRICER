package com.cardpricer.service;

import com.cardpricer.util.AppDataDirectory;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages autosave of in-progress trade sessions to disk for crash recovery.
 *
 * <p>The session file lives at {@code %APPDATA%\OCC_Trade_Pricer\session\autosave.json}.
 * It is written every 60 seconds by {@link com.cardpricer.gui.panel.TradePanel}
 * and deleted on a successful save or manual clear.
 */
public class TradeSessionService {

    /**
     * A single table row captured for autosave purposes.
     *
     * @param code      display code shown in the table (e.g. "TDM 3f")
     * @param cardName  card name
     * @param condition condition string (e.g. "NM")
     * @param qty       quantity
     * @param unitPrice unit price
     */
    public record SessionRow(
            String code,
            String cardName,
            String condition,
            int qty,
            BigDecimal unitPrice) {}

    /**
     * A saved session containing trader/customer info and all table rows.
     *
     * @param traderName   trader name (may be empty)
     * @param customerName customer name (may be empty)
     * @param rows         list of session rows
     */
    public record SavedSession(
            String traderName,
            String customerName,
            List<SessionRow> rows) {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Returns {@code true} if an autosave file exists. */
    public static boolean hasAutosave() {
        return Files.exists(autosavePath());
    }

    /** Deletes the autosave file if it exists. */
    public static void clearAutosave() {
        try {
            Files.deleteIfExists(autosavePath());
        } catch (Exception ignored) {}
    }

    /**
     * Serialises the current session to the autosave file.
     *
     * @param traderName   trader name field content
     * @param customerName customer name field content
     * @param rows         current table rows
     */
    public static void save(String traderName, String customerName, List<SessionRow> rows) {
        try {
            JSONObject root = new JSONObject();
            root.put("traderName",   traderName   == null ? "" : traderName);
            root.put("customerName", customerName == null ? "" : customerName);

            JSONArray arr = new JSONArray();
            for (SessionRow row : rows) {
                JSONObject obj = new JSONObject();
                obj.put("code",      row.code());
                obj.put("cardName",  row.cardName());
                obj.put("condition", row.condition());
                obj.put("qty",       row.qty());
                obj.put("unitPrice", row.unitPrice().toPlainString());
                arr.put(obj);
            }
            root.put("rows", arr);

            Path path = autosavePath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Never crash the app over an autosave failure
        }
    }

    /**
     * Loads the autosave file and returns a {@link SavedSession}, or {@code null}
     * if the file cannot be read or is malformed.
     *
     * @return parsed session, or {@code null} on any error
     */
    public static SavedSession load() {
        try {
            String content = Files.readString(autosavePath(), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(content);
            String traderName   = root.optString("traderName",   "");
            String customerName = root.optString("customerName", "");

            JSONArray arr = root.optJSONArray("rows");
            List<SessionRow> rows = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String code      = obj.optString("code",      "MISC 1");
                    String cardName  = obj.optString("cardName",  "Unknown Card");
                    String condition = obj.optString("condition", "NM");
                    int qty          = obj.optInt("qty", 1);
                    BigDecimal price;
                    try {
                        price = new BigDecimal(obj.optString("unitPrice", "0.00"));
                    } catch (NumberFormatException e) {
                        price = BigDecimal.ZERO;
                    }
                    rows.add(new SessionRow(code, cardName, condition, qty, price));
                }
            }
            return new SavedSession(traderName, customerName, rows);
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static Path autosavePath() {
        return new File(AppDataDirectory.root(), "session/autosave.json").toPath();
    }
}
