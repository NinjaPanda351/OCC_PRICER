package com.cardpricer.service;

import com.cardpricer.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InventoryStatusSyncTest {
    @TempDir Path temp;

    private TradeRepository repository(String workstation) throws Exception {
        return TradeHistoryService.repository(temp.resolve(workstation).resolve("trades").toString());
    }
    private TradeRecord record(UUID id, long revision) {
        return new TradeRecord(temp.resolve("trade.txt").toString(),LocalDateTime.of(2026,9,15,12,0),
                "Customer","Employee","credit",new BigDecimal("24.00"),3,id,revision,false);
    }
    private boolean inventoried(TradeRepository repository,TradeRecord record) throws Exception {
        return repository.inventoryStatuses(List.of(record)).getFirst().inventoried;
    }
    private Path share() throws Exception { return Files.createDirectory(temp.resolve("shared")); }
    private InventoryStatusSyncService.Result sync(TradeRepository repository,Path shared) throws Exception {
        return new InventoryStatusSyncService(repository).sync(shared);
    }
    private void copyOutputs(Path source,Path shared) throws Exception {
        try (var files=Files.list(source)) {
            for (Path file:files.toList()) Files.copy(file,shared.resolve(file.getFileName()),StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test void checkAndUncheckTravelBothDirectionsAndAppearInSharedTradeHistory() throws Exception {
        var a=repository("a");var b=repository("b");Path shared=share();
        var draft=TradeRepositoryTest.draft("TST");Path outputs=temp.resolve("a/trades");
        new TradeApplicationService(a,outputs).finalizeTrade(draft);copyOutputs(outputs,shared);
        var local=a.history(outputs).getFirst();a.setInventoried(local,true);
        assertEquals(1,sync(a,shared).published());assertEquals(1,sync(b,shared).imported());
        var remote=TradeHistoryService.loadAll(temp.resolve("b/trades").toString(),shared.toString());
        assertEquals(1,remote.size());assertEquals(local.tradeId,remote.getFirst().tradeId);
        assertTrue(remote.getFirst().inventoried);
        b.setInventoried(remote.getFirst(),false);sync(b,shared);sync(a,shared);
        assertFalse(inventoried(a,local));assertFalse(inventoried(b,local));
        assertEquals(2,a.inventoryChanges().size());
        assertEquals(0,sync(a,shared).published());assertEquals(0,sync(b,shared).imported());
    }

    @Test void disconnectedChangeSurvivesRestartAndDoesNotCreateAFakeShare() throws Exception {
        var a=repository("a");var trade=record(UUID.randomUUID(),3);a.setInventoried(trade,true);
        Path shared=temp.resolve("missing-share");
        assertThrows(java.io.IOException.class,()->sync(a,shared));assertFalse(Files.exists(shared));
        var restarted=repository("a");assertTrue(inventoried(restarted,trade));
        Files.createDirectory(shared);sync(restarted,shared);
        var b=repository("b");sync(b,shared);assertTrue(inventoried(b,trade));
        b.setInventoried(trade,false);
        // A new service instance has no in-memory queue to depend on.
        sync(repository("b"),shared);sync(restarted,shared);assertFalse(inventoried(restarted,trade));
    }

    @Test void concurrentOppositeChangesConvergeToReviewRegardlessOfArrivalOrder() throws Exception {
        var a=repository("a");var b=repository("b");Path shared=share();
        var trade=record(UUID.randomUUID(),1);
        a.setInventoried(trade,true);b.setInventoried(trade,false);
        sync(a,shared);sync(b,shared);sync(a,shared);
        assertFalse(inventoried(a,trade));assertFalse(inventoried(b,trade));
        var c=repository("c");sync(c,shared);assertFalse(inventoried(c,trade));
        // Once the conflict has been seen, an explicit check supersedes both operations.
        b.setInventoried(trade,true);sync(b,shared);sync(a,shared);sync(c,shared);
        assertTrue(inventoried(a,trade));assertTrue(inventoried(c,trade));
        assertEquals(3,a.inventoryChanges().size());
    }

    @Test void oldRevisionChangesNeverOverrideNewRevisionAndClocksDoNotDetermineOrder() throws Exception {
        var a=repository("a");var b=repository("b");Path shared=share();UUID id=UUID.randomUUID();
        var old=record(id,1);var updated=record(id,2);
        // Even a clock set years ahead cannot win over a causally newer checkbox change.
        a.importInventoryChange(new InventoryStatusChange(UUID.randomUUID(),old.historyKey(),1,1,true,Instant.parse("2099-01-01T00:00:00Z")));
        sync(a,shared);sync(b,shared);
        assertFalse(inventoried(b,updated));
        b.setInventoried(old,false);sync(b,shared);sync(a,shared);assertFalse(inventoried(a,old));
        b.setInventoried(updated,true);sync(b,shared);sync(a,shared);
        a.setInventoried(old,false);sync(a,shared);sync(b,shared);
        assertTrue(inventoried(a,updated));assertTrue(inventoried(b,updated));
    }

    @Test void migratedLocalStatusesArePreservedAndSharedOnlyOnce() throws Exception {
        Path database=temp.resolve("a/ledger/trades.sqlite");Files.createDirectories(database.getParent());
        var trade=record(UUID.randomUUID(),4);
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+database);var s=c.createStatement()) {
            s.execute("CREATE TABLE inventory_status(record_key TEXT PRIMARY KEY, revision INTEGER NOT NULL, inventoried INTEGER NOT NULL, updated_at TEXT NOT NULL)");
            s.execute("PRAGMA user_version=2");
            try(var insert=c.prepareStatement("INSERT INTO inventory_status VALUES(?,?,?,?)")) {
                insert.setString(1,trade.historyKey());insert.setLong(2,4);insert.setBoolean(3,true);
                insert.setString(4,"2026-09-15T00:00:00Z");insert.executeUpdate();
            }
        }
        var a=repository("a");assertTrue(inventoried(a,trade));assertEquals(1,a.inventoryChanges().size());
        assertEquals(a.inventoryChanges(),repository("a").inventoryChanges());
        Path shared=share();sync(a,shared);var b=repository("b");sync(b,shared);assertTrue(inventoried(b,trade));
        assertEquals(1,b.inventoryChanges().size());
    }

    @Test void malformedAndConflictingDocumentsDoNotBlockValidUpdatesOrOverwriteOriginals() throws Exception {
        var a=repository("a");var b=repository("b");Path shared=share();var trade=record(UUID.randomUUID(),1);
        a.setInventoried(trade,true);sync(a,shared);
        Path directory=shared.resolve(InventoryStatusSyncService.DIRECTORY);
        Files.writeString(directory.resolve("broken.json"),"{incomplete");
        Files.writeString(directory.resolve(".pos-status-unfinished.tmp"),"partial content");
        assertTrue(sync(b,shared).errors()>0);assertTrue(inventoried(b,trade));
        var change=a.inventoryChanges().getFirst();Path original=directory.resolve(change.id()+".json");
        String conflicting=change.toJson().put("inventoried",false).toString();Files.writeString(original,conflicting);
        assertTrue(sync(a,shared).errors()>0);assertTrue(inventoried(a,trade));
        assertEquals(conflicting,Files.readString(original));
        assertEquals(1,a.inventoryChanges().size());
    }

    @Test void simultaneousPublishersDoNotLoseEachOthersUpdates() throws Exception {
        var a=repository("a");var b=repository("b");Path shared=share();
        var first=record(UUID.randomUUID(),1);var second=record(UUID.randomUUID(),1);
        a.setInventoried(first,true);b.setInventoried(second,true);
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var one=workers.submit(()->sync(a,shared));var two=workers.submit(()->sync(b,shared));
            assertEquals(0,one.get().errors());assertEquals(0,two.get().errors());
        }
        sync(a,shared);sync(b,shared);
        assertTrue(inventoried(a,first));assertTrue(inventoried(a,second));
        assertTrue(inventoried(b,first));assertTrue(inventoried(b,second));
        try(var files=Files.list(shared.resolve(InventoryStatusSyncService.DIRECTORY))) { assertEquals(2,files.count()); }
    }

    @Test void legacyReceiptStatusUsesFilenameAcrossDifferentWorkstationPaths() throws Exception {
        var a=repository("a");var b=repository("b");Path shared=share();
        var first=new TradeRecord(temp.resolve("a/2025-01-01_customer.txt").toString(),LocalDateTime.now(),"Customer","Staff","credit",BigDecimal.ONE,1);
        var second=new TradeRecord(temp.resolve("b/2025-01-01_customer.txt").toString(),first.date,"Customer","Staff","credit",BigDecimal.ONE,1);
        a.setInventoried(first,true);sync(a,shared);sync(b,shared);assertTrue(inventoried(b,second));
        b.setInventoried(second,false);sync(b,shared);sync(a,shared);assertFalse(inventoried(a,first));
    }

    @Test void earlyStructuredReceiptUsesJsonRevisionForNetworkStatus() throws Exception {
        var a=repository("a");var b=repository("b");Path shared=share();
        var draft=TradeRepositoryTest.draft("TST");String prefix="trade_"+draft.id();
        Files.writeString(shared.resolve(prefix+".txt"),"Trade ID: "+draft.id()+"\nCustomer Name: Customer\nTotal Cards: 3\nTotal Value: $24.00\n");
        Files.writeString(shared.resolve(prefix+".json"),draft.toJson().toString());
        a.setInventoried(record(draft.id(),draft.revision()),true);sync(a,shared);sync(b,shared);
        var remote=TradeHistoryService.loadAll(temp.resolve("b/trades").toString(),shared.toString()).getFirst();
        assertEquals(draft.revision(),remote.revision);assertTrue(remote.inventoried);
    }
}
