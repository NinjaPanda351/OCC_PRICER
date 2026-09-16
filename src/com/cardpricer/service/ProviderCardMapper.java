package com.cardpricer.service;

import com.cardpricer.model.Card;
import org.json.JSONArray;
import org.json.JSONObject;

/** One lossless mapper for provider responses, catalog snapshots and trade recovery. */
public final class ProviderCardMapper {
    private ProviderCardMapper() {}
    public static Card fromJson(JSONObject json) {
        Card card = new Card(json.getString("name"), json.getString("set").toUpperCase(java.util.Locale.ROOT),
                json.getString("collector_number"));
        card.setProviderId(json.optString("id", ""));
        card.setPriceObservedAt(json.optString("price_observed_at","unknown"));
        card.setLanguage(json.optString("lang", "en"));
        card.setRarity(json.optString("rarity", "unknown"));
        card.setArtist(json.optString("artist", ""));
        JSONObject prices = json.optJSONObject("prices");
        if (prices != null) {
            card.setPrice(prices.optString("usd", null));
            card.setFoilPrice(prices.optString("usd_foil", null));
            card.setEtchedPrice(prices.optString("usd_etched", null));
        }
        card.setReserved(json.optBoolean("reserved", false));
        JSONArray effects = json.optJSONArray("frame_effects");
        if (effects != null) card.setFrameEffects(effects.toList().stream().map(Object::toString).toList());
        JSONArray finishes = json.optJSONArray("finishes");
        if (finishes != null) card.setFinishes(finishes.toList().stream().map(Object::toString).toList());
        JSONObject images = json.optJSONObject("image_uris");
        JSONArray faces = json.optJSONArray("card_faces");
        if (faces != null && !faces.isEmpty()) {
            card.setFacesJson(faces.toString());
            JSONObject face = faces.getJSONObject(0);
            if (images == null) images = face.optJSONObject("image_uris");
            if (card.getArtist().isBlank()) card.setArtist(face.optString("artist", ""));
        }
        if (images != null) card.setImageUrl(images.optString("normal", null));
        return card;
    }
    public static JSONObject toJson(Card card) {
        JSONObject json = new JSONObject().put("name", card.getName()).put("set", card.getSetCode())
                .put("collector_number", card.getCollectorNumber()).put("id", card.getProviderId())
                .put("lang", card.getLanguage()).put("rarity", card.getRarity()).put("artist", card.getArtist())
                .put("reserved", card.isReserved()).put("frame_effects", card.getFrameEffects())
                .put("finishes", card.getFinishes());
        json.put("price_observed_at",card.getPriceObservedAt());
        JSONObject prices = new JSONObject();
        if (card.hasNormalPrice()) prices.put("usd", card.getPrice());
        if (card.hasFoilPrice()) prices.put("usd_foil", card.getFoilPrice());
        if (card.hasEtchedPrice()) prices.put("usd_etched", card.getEtchedPrice());
        json.put("prices", prices);
        if (card.getImageUrl() != null) json.put("image_uris", new JSONObject().put("normal", card.getImageUrl()));
        if (card.getFacesJson() != null) json.put("card_faces", new JSONArray(card.getFacesJson()));
        return json;
    }
}
