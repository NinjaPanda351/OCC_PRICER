package com.cardpricer.model;
import java.util.List;
/** Immutable provider record stored in the shared catalog. Mutable Cards are detached adapters. */
public record ProviderPrinting(PrintingIdentity identity,String name,String rarity,String normalPrice,String foilPrice,
                               String etchedPrice,List<String> effects,String artist,String imageUrl,boolean reserved,
                               List<String> finishes,String facesJson,String observedAt) {
    public ProviderPrinting { effects=List.copyOf(effects);finishes=List.copyOf(finishes); }
    public static ProviderPrinting from(Card card) {
        return new ProviderPrinting(card.identity(),card.getName(),card.getRarity(),card.getPrice(),card.getFoilPrice(),
                card.getEtchedPrice(),card.getFrameEffects(),card.getArtist(),card.getImageUrl(),card.isReserved(),
                card.getFinishes(),card.getFacesJson(),card.getPriceObservedAt());
    }
    public Card toCard() {
        Card card=new Card(name,identity.set(),identity.collectorNumber());
        card.setProviderId(identity.providerId());card.setLanguage(identity.language());card.setRarity(rarity);
        card.setPrice(normalPrice);card.setFoilPrice(foilPrice);card.setEtchedPrice(etchedPrice);card.setFrameEffects(effects);
        card.setArtist(artist);card.setImageUrl(imageUrl);card.setReserved(reserved);card.setFinishes(finishes);
        card.setFacesJson(facesJson);card.setPriceObservedAt(observedAt);return card;
    }
}
