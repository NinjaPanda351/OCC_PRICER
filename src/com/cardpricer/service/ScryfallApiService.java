package com.cardpricer.service;

import com.cardpricer.exception.ScryfallApiException;
import com.cardpricer.model.Card;
import com.cardpricer.util.SetList;
import com.cardpricer.util.VintageUtil;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides access to the Scryfall REST API for fetching individual cards and
 * full set listings.  All network calls are synchronous; callers are responsible
 * for running them off the EDT.
 */
public class ScryfallApiService {
    private static final String SEARCH_API = "https://api.scryfall.com/cards/search";
    private static final String CARD_API = "https://api.scryfall.com/cards";
    private static final String USER_AGENT = "CardPricerApp/1.0";
    private static final int RATE_LIMIT_MS = 100; // Scryfall allows 10 requests/second

    /**
     * Fetches all cards from a specific set
     * @param setCode The three-letter set code (e.g., "MKM")
     * @return List of all cards in the set
     * @throws ScryfallApiException if API call fails
     */
    public List<Card> fetchCardsFromSet(String setCode) throws ScryfallApiException {
        List<Card> allCards = new ArrayList<>();
        String nextPage = SEARCH_API + "?q=set:" + setCode.toLowerCase() + "&unique=prints";

        try {
            while (nextPage != null) {
                System.out.println("Fetching page from Scryfall...");
                JSONObject response = makeApiCall(nextPage);
                JSONArray data = response.getJSONArray("data");

                // Parse each card in the response
                for (int i = 0; i < data.length(); i++) {
                    Card card = parseCardFromJson(data.getJSONObject(i));
                    allCards.add(card);
                }

                // Check if there are more pages
                if (response.getBoolean("has_more")) {
                    nextPage = response.getString("next_page");
                    Thread.sleep(RATE_LIMIT_MS); // Respect API rate limits
                } else {
                    nextPage = null;
                }
            }

            System.out.println("Successfully fetched " + allCards.size() + " cards");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScryfallApiException("Request interrupted", e);
        }

        return allCards;
    }

    /**
     * Fetches a specific card by set and collector number
     * @param theSetCode The set code (e.g., "MKM")
     * @param theCollectorNumber The collector number (e.g., "173")
     * @return The card if found
     * @throws ScryfallApiException if card not found or API call fails
     */
    public Card fetchCard(final String theSetCode, final String theCollectorNumber)
            throws ScryfallApiException {
        try {
            // Resolve friendly set-name aliases (e.g. "alpha" → "lea") before the call.
            String scryfallSetCode = VintageUtil.resolveSetAlias(theSetCode);

            // Use the multi-arg URI constructor so that any special characters in the
            // collector number (e.g. ★ for surge foils) are correctly percent-encoded
            // in the path (★ = U+2605 → %E2%98%85).  toASCIIString() returns the
            // fully-encoded URL safe to pass to makeApiCall().
            java.net.URI uri = new java.net.URI(
                    "https", "api.scryfall.com",
                    "/cards/" + scryfallSetCode + "/" + theCollectorNumber,
                    null, null);
            JSONObject json = makeApiCall(uri.toASCIIString());
            return parseCardFromJson(json);
        } catch (java.net.URISyntaxException e) {
            throw new ScryfallApiException("Card URL invalid: " + e.getMessage(), e);
        }
    }

    /**
     * Makes an HTTP GET request to Scryfall API
     * @param urlStr The full URL to call
     * @return JSONObject containing the response
     * @throws ScryfallApiException if the call fails
     */
    public JSONObject makeApiCall(String urlStr) throws ScryfallApiException {
        HttpURLConnection conn = null;
        try {
            URI uri = new URI(urlStr);
            URL url = uri.toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(10000); // 10 second timeout
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();

            // Handle different response codes
            if (responseCode == 404) {
                throw new ScryfallApiException("Card or set not found");
            } else if (responseCode != 200) {
                throw new ScryfallApiException(
                        "API call failed with response code: " + responseCode);
            }

            // Read the response
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            return new JSONObject(response.toString());

        } catch (Exception e) {
            if (e instanceof ScryfallApiException) {
                throw (ScryfallApiException) e;
            }
            throw new ScryfallApiException("Failed to fetch data from Scryfall: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Parses a Card object from Scryfall JSON response
     * @param json The JSON object from Scryfall API
     * @return A populated Card object
     */
    public Card parseCardFromJson(JSONObject json) {
        Card card = new Card();

        // PLST (The List) override: store as the original set, not as "plst"
        if ("plst".equals(json.getString("set"))) {
            String plstNum = json.getString("collector_number"); // e.g. "ARB-1"
            int idx = plstNum.lastIndexOf('-');
            if (idx > 0) {
                card.setName(json.getString("name"));
                card.setSetCode(plstNum.substring(0, idx).toUpperCase());  // "ARB"
                card.setCollectorNumber(plstNum.substring(idx + 1));       // "1"
                card.setRarity(json.getString("rarity"));
                card.setArtist(json.getString("artist"));
                if (json.has("prices")) {
                    JSONObject prices = json.getJSONObject("prices");
                    card.setPrice(!prices.isNull("usd") ? prices.getString("usd") : null);
                    card.setFoilPrice(!prices.isNull("usd_foil") ? prices.getString("usd_foil") : null);
                    card.setEtchedPrice(!prices.isNull("usd_etched") ? prices.getString("usd_etched") : null);
                }
                if (json.has("reserved")) card.setReserved(json.getBoolean("reserved"));
                extractImageUrl(json, card);
                return card;
            }
        }

        String rawCollectorNumber = json.getString("collector_number");

        // Remove Scryfall special markers like ★
        String cleanedCollectorNumber =
                rawCollectorNumber.replaceAll("[^0-9A-Za-z]", "");

        // Set required fields
        card.setName(json.getString("name"));

        String originalSetCode = json.getString("set");
        String updatedSetCode = SetList.getApiCode(originalSetCode);
        card.setSetCode(updatedSetCode);

        card.setCollectorNumber(cleanedCollectorNumber);
        card.setRarity(json.getString("rarity"));
        card.setArtist(json.getString("artist"));

        extractImageUrl(json, card);

        // Handle prices (nested in "prices" object)
        if (json.has("prices")) {
            JSONObject prices = json.getJSONObject("prices");

            // Normal price
            if (!prices.isNull("usd")) {
                card.setPrice(prices.getString("usd"));
            } else {
                card.setPrice(null); // Will be set to "N/A" by setter
            }

            // Foil price
            if (!prices.isNull("usd_foil")) {
                card.setFoilPrice(prices.getString("usd_foil"));
            } else {
                card.setFoilPrice(null); // Will be set to "N/A" by setter
            }

            // Etched price
            if (!prices.isNull("usd_etched")) {
                card.setEtchedPrice(prices.getString("usd_etched"));
            } else {
                card.setEtchedPrice(null); // Will be set to "N/A" by setter
            }
        }

        // Reserved List flag
        if (json.has("reserved")) {
            card.setReserved(json.getBoolean("reserved"));
        }

        // Frame effects (showcase, extendedart, borderless, etched, etc.)
        if (json.has("frame_effects")) {
            JSONArray fxArray = json.getJSONArray("frame_effects");
            List<String> frameEffects = new ArrayList<>();
            for (int i = 0; i < fxArray.length(); i++) {
                frameEffects.add(fxArray.getString(i));
            }
            card.setFrameEffects(frameEffects);
        }

        return card;
    }

    /**
     * Extracts the "normal" image URL from a Scryfall card JSON object.
     * Handles both single-faced cards (image_uris) and double-faced cards (card_faces[0].image_uris).
     */
    private void extractImageUrl(JSONObject json, Card card) {
        if (json.has("image_uris")) {
            JSONObject imageUris = json.getJSONObject("image_uris");
            if (imageUris.has("normal")) {
                card.setImageUrl(imageUris.getString("normal"));
            }
        } else if (json.has("card_faces")) {
            JSONArray faces = json.getJSONArray("card_faces");
            if (faces.length() > 0) {
                JSONObject front = faces.getJSONObject(0);
                if (front.has("image_uris")) {
                    JSONObject imageUris = front.getJSONObject("image_uris");
                    if (imageUris.has("normal")) {
                        card.setImageUrl(imageUris.getString("normal"));
                    }
                }
            }
        }
    }

    /**
     * Test main method - remove this later or move to a test class
     */
    static void main(String[] args) {
        ScryfallApiService service = new ScryfallApiService();

        System.out.println("=== Testing Single Card Fetch ===");
        try {
            Card card = service.fetchCard("40k", "149");
            System.out.println("Found: " + card);
            System.out.println("Normal Price: $" + card.getPrice());
            System.out.println("Foil Price: $" + card.getFoilPrice());
        } catch (ScryfallApiException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n=== Testing Set Fetch ===");
        try {
            List<Card> cards = service.fetchCardsFromSet("tla");
            System.out.println("\nFirst 5 cards:");
            for (int i = 0; i < Math.min(5, cards.size()); i++) {
                System.out.println(cards.get(i));
            }
        } catch (ScryfallApiException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}