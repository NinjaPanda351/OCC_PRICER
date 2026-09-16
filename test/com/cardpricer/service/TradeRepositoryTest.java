package com.cardpricer.service;
import com.cardpricer.model.*;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TradeRepositoryTest {
    @TempDir Path temp;
    static TradeDraft draft(String set) {
        Card card = new Card("日本語 Éowyn", set, "73★"); card.setRarity("rare"); card.setPrice("10");
        card.setProviderId("provider-id");
        var line = new TradeLine(UUID.randomUUID(), card.identity(), ProviderCardMapper.toJson(card).toString(),
                Finish.ETCHED, Condition.LP, 3, new BigDecimal("10.00"), new BigDecimal("8.00"), new BigDecimal("0.80"), new BigDecimal("0.40"));
        return new TradeDraft(UUID.randomUUID(), 1, "Operator", "Customer", "", "123", "check", BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(line), "2026-09-15T21:30:00Z", "test-rates");
    }
    @Test void draftRoundTripPreservesPrintingFinishBaseAndPayment() {
        var draft = draft("PLST");
        var restored = TradeDraft.fromJson(new JSONObject(draft.toJson().toString()));
        assertEquals(draft, restored);
        assertEquals("73★", restored.lines().getFirst().item().getCard().getCollectorNumber());
        assertEquals(new BigDecimal("10.00"), restored.lines().getFirst().item().getUnitPrice());
    }
    @Test void failedOutputSurvivesRestartAndRetryDoesNotCommitAnotherTrade() throws Exception {
        var draft = draft("TST");
        Path database = temp.resolve("ledger.sqlite");
        var repository = new TradeRepository(database);
        Path blocked = temp.resolve("blocked"); Files.writeString(blocked, "file instead of folder");
        assertEquals(3, new TradeApplicationService(repository, blocked).finalizeTrade(draft));
        assertTrue(repository.isCommitted(draft.id()));
        assertEquals(3, repository.pending().size());
        var restarted = new TradeRepository(database);
        Path output = temp.resolve("outputs");
        assertEquals(0, new TradeApplicationService(restarted, output).finalizeTrade(draft));
        assertEquals(0, new TradeApplicationService(restarted, output).finalizeTrade(draft));
        try (var files = Files.list(output)) { assertEquals(3, files.count()); }
        assertTrue(restarted.pending().isEmpty());
        String prefix=TradeApplicationService.outputPrefix(draft,false);
        assertTrue(prefix.contains(" - Customer - Operator - "));
        for (String extension:List.of(".txt",".json",".csv")) assertTrue(Files.exists(output.resolve(prefix+extension)));
        assertTrue(Files.readString(output.resolve(prefix + ".txt")).contains("日本語 Éowyn"));
    }
    @Test void miscOnlyCommitsWithoutStockCsv() throws Exception {
        var repository = new TradeRepository(temp.resolve("ledger.sqlite"));
        var draft = draft("MISC");
        Path output = temp.resolve("outputs");
        assertEquals(0, new TradeApplicationService(repository, output).finalizeTrade(draft));
        assertTrue(repository.isCommitted(draft.id()));
        try (var files = Files.list(output)) { assertEquals(2, files.count()); }
    }
    @Test void conflictingOutputIsRetainedAndReportedPending() throws Exception {
        var repository = new TradeRepository(temp.resolve("ledger.sqlite"));
        var draft = draft("TST");
        repository.commit(draft, List.of(new TradeRepository.Output("same.txt", "correct")));
        Path output = temp.resolve("outputs"); Files.createDirectories(output); Files.writeString(output.resolve("same.txt"), "changed");
        assertEquals(1, repository.retryOutputs(output));
        assertEquals("changed", Files.readString(output.resolve("same.txt")));
        assertEquals(1, repository.pending().getFirst().attempts());
    }
    @Test void failedCommitRollsBackSnapshotAndJobs() throws Exception {
        var repository = new TradeRepository(temp.resolve("ledger.sqlite"));
        var draft = draft("TST");
        var output = new TradeRepository.Output("duplicate.txt", "one");
        assertThrows(java.sql.SQLException.class, () -> repository.commit(draft, List.of(output, output)));
        assertFalse(repository.isCommitted(draft.id())); assertTrue(repository.pending().isEmpty());
    }
    @Test void editingAnApprovedTradeCannotSilentlyReuseItsOutputs() throws Exception {
        var repository=new TradeRepository(temp.resolve("ledger.sqlite"));var original=draft("TST");
        var service=new TradeApplicationService(repository,temp.resolve("out"));service.finalizeTrade(original);
        var edited=new TradeDraft(original.id(),2,"Changed",original.customer(),original.identification(),original.checkNumber(),
                original.payment(),original.credit(),original.check(),original.lines());
        assertThrows(IllegalStateException.class,()->service.finalizeTrade(edited));
        assertTrue(repository.isCommitted(original.id()));
    }
    @Test void legacyDraftCanBeSavedButCannotBeApprovedWithInventedIdentity() throws Exception {
        var original=draft("TST");
        var legacy=original.lines().getFirst();var card=legacy.card();card.setProviderId("legacy-unverified");
        var restoredLine=new TradeLine(legacy.id(),card.identity(),ProviderCardMapper.toJson(card).toString(),legacy.finish(),legacy.condition(),
                legacy.quantity(),legacy.base(),legacy.valuation(),legacy.creditRate(),legacy.checkRate());
        var restored=new TradeDraft(original.id(),1,original.trader(),original.customer(),"","",original.payment(),
                original.credit(),original.check(),List.of(restoredLine));
        var repository=new TradeRepository(temp.resolve("ledger.sqlite"));repository.saveDraft(restored);
        assertThrows(IllegalArgumentException.class,()->new TradeApplicationService(repository,temp.resolve("out")).finalizeTrade(restored));
        assertFalse(repository.isCommitted(restored.id()));assertFalse(Files.exists(temp.resolve("out")));
    }
}
