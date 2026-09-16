package com.cardpricer.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SetCatalogServiceTest {
    @TempDir Path temp;
    private final Instant now = Instant.parse("2026-09-16T12:00:00Z");

    private static JSONObject set(String code, String name, String type, String date) {
        return new JSONObject().put("code", code).put("name", name).put("set_type", type).put("released_at", date);
    }

    private SetCatalogService service(JSONArray data, Instant time) {
        return new SetCatalogService(new ScryfallApiService() {
            @Override public JSONObject makeApiCall(String url) {
                assertEquals("https://api.scryfall.com/sets", url);
                return new JSONObject().put("object", "list").put("has_more", false).put("data", data);
            }
        }, temp.resolve("sets.json"), Clock.fixed(time, ZoneOffset.UTC));
    }

    @Test void discoversNewSetsFiltersByTypeAndPreservesCustomCodes() throws Exception {
        JSONArray data = new JSONArray()
                .put(set("chk", "Champions of Kamigawa", "expansion", "2004-10-01"))
                .put(set("pip", "Fallout", "commander", "2024-03-08"))
                .put(set("tdm", "Tarkir: Dragonstorm", "expansion", "2025-04-11"))
                .put(set("new", "A Future Set", "expansion", "2027-01-01"))
                .put(set("anew", "A Future Set Art Series", "memorabilia", "2027-01-01"))
                .put(set("fnew", "A Future Set Front Cards", "memorabilia", "2027-01-01"))
                .put(set("jnew", "A Future Set Jumpstart Front Cards", "memorabilia", "2027-01-01"))
                .put(set("mem", "Other Memorabilia", "memorabilia", "2026-01-01"))
                .put(set("pnew", "Future Promos", "promo", "2027-01-01"))
                .put(set("tnew", "Future Tokens", "token", "2027-01-01"));
        var catalog = service(data, now).refresh();
        assertEquals(List.of("NEW", "MEM", "TDM", "PIP", "COK"), catalog.sets().stream().map(SetCatalogService.Entry::code).toList());
        var old = catalog.sets().getLast();
        assertEquals("chk", old.apiCode());
        assertTrue(old.matches("champions"));
        assertTrue(old.matches("chk"));
        assertTrue(old.matches("cok"));
        assertEquals(catalog, service(data, now).loadCached());
    }

    @Test void cacheIsFreshForOneDayAndBundledListRequiresUpdate() throws Exception {
        var service = service(new JSONArray().put(set("new", "New Set", "expansion", "2026-09-01")), now);
        assertTrue(service.needsRefresh(service.loadCached()));
        var catalog = service.refresh();
        assertFalse(service.needsRefresh(catalog));
        assertFalse(service(new JSONArray(), now.plusSeconds(86399)).needsRefresh(catalog));
        assertTrue(service(new JSONArray(), now.plusSeconds(86400)).needsRefresh(catalog));
    }

    @Test void providerCodesThatCollideWithPosAliasesRemainDistinct() throws Exception {
        var catalog = service(new JSONArray()
                .put(set("jvc", "Duel Decks Anthology: Jace vs. Chandra", "duel_deck", "2014-12-05"))
                .put(set("dd2", "Duel Decks: Jace vs. Chandra", "duel_deck", "2008-11-07")), now).refresh();
        assertEquals(List.of("JVC (Scryfall)", "JVC"), catalog.sets().stream().map(SetCatalogService.Entry::code).toList());
        assertEquals(List.of("jvc", "dd2"), catalog.sets().stream().map(SetCatalogService.Entry::apiCode).toList());
        assertEquals(catalog, service(new JSONArray(), now).loadCached());
    }

    @Test void failedOrMalformedUpdatePreservesLastGoodCache() throws Exception {
        var good = service(new JSONArray().put(set("new", "New Set", "expansion", "2026-09-01")), now).refresh();
        String saved = Files.readString(temp.resolve("sets.json"));
        for (JSONArray data : List.of(new JSONArray(), new JSONArray().put(new JSONObject().put("code", "bad")),
                new JSONArray().put(set("new", "New Set", "expansion", "invalid")))) {
            assertThrows(Exception.class, () -> service(data, now.plusSeconds(86400)).refresh());
            assertEquals(saved, Files.readString(temp.resolve("sets.json")));
        }
        var offline = new SetCatalogService(new ScryfallApiService() {
            @Override public JSONObject makeApiCall(String url) { throw new IllegalStateException("Offline"); }
        }, temp.resolve("sets.json"), Clock.fixed(now, ZoneOffset.UTC));
        assertThrows(Exception.class, offline::refresh);
        assertEquals(good, offline.loadCached());
    }

    @Test void corruptCacheFallsBackAndUnwritableCacheStillReturnsUpdatedSets() throws Exception {
        Files.writeString(temp.resolve("sets.json"), "broken");
        var service = service(new JSONArray().put(set("new", "New Set", "expansion", "2026-09-01")), now);
        assertTrue(service.needsRefresh(service.loadCached()));
        Files.delete(temp.resolve("sets.json"));
        Files.createDirectory(temp.resolve("sets.json"));
        Files.writeString(temp.resolve("sets.json/keep"), "keep");
        var fresh = service.refresh();
        assertEquals("NEW", fresh.sets().getFirst().code());
        assertTrue(fresh.notice().contains("could not save"));
    }
}
