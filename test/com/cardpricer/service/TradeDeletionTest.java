package com.cardpricer.service;

import com.cardpricer.model.TradeDraft;
import com.cardpricer.util.AtomicFiles;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TradeDeletionTest {
    @TempDir Path temp;
    private Path local(String station) { return temp.resolve(station).resolve("trades"); }
    private TradeRepository repo(String station) throws Exception { return TradeHistoryService.repository(local(station).toString()); }
    private TradeDeletionService deletions(String station,Path shared) throws Exception { return new TradeDeletionService(repo(station),local(station),shared); }
    private static TradeDraft corrected(TradeDraft draft,String customer) {
        return new TradeDraft(draft.id(),draft.revision()+1,draft.trader(),customer,draft.identification(),draft.checkNumber(),
                draft.payment(),draft.credit(),draft.check(),draft.lines(),draft.quotedAt(),draft.rateRevision());
    }
    private TradeDraft save(String station) throws Exception {
        var draft=TradeRepositoryTest.draft("TST");new TradeApplicationService(repo(station),local(station)).finalizeTrade(draft);return draft;
    }
    private void copy(Path from,Path to) throws Exception {
        Files.createDirectories(to);try(var files=Files.list(from)) {
            for(Path file:files.filter(Files::isRegularFile).toList()) Files.copy(file,to.resolve(file.getFileName()),StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private static List<Path> exports(Path directory) throws Exception {
        if(!Files.isDirectory(directory)) return List.of();
        try(var files=Files.list(directory)) { return files.filter(Files::isRegularFile).filter(p->p.toString().matches("(?i).+\\.(csv|txt|json|pdf)")).toList(); }
    }

    @Test void deletingPendingTradeSurvivesRestartAndCannotBeReapproved() throws Exception {
        var repository=repo("A");var draft=TradeRepositoryTest.draft("TST");
        repository.commit(draft,TradeApplicationService.outputs(draft,false)); // No exports written yet.
        var session=new TradeHistoryService.Session(local("A").toString(),"");var before=session.cached();
        var record=before.records().getFirst();assertEquals(3,repository.pending().size());
        assertEquals(0,deletions("A",null).delete(record).pending());
        var restarted=repo("A");assertTrue(restarted.pending().isEmpty());assertEquals(0,restarted.retryOutputs(local("A")));
        assertTrue(exports(local("A")).isEmpty());assertTrue(session.cached().records().isEmpty());
        assertTrue(session.search(session.cached(),draft.customer()).isEmpty());
        assertThrows(IOException.class,()->session.receipt(before,record));
        assertThrows(SQLException.class,()->new TradeApplicationService(restarted,local("A")).finalizeTrade(draft));
        assertThrows(SQLException.class,()->restarted.revise(corrected(draft,"Cannot revive"),draft.revision(),List.of()));
    }

    @Test void deletionOnOtherComputerRemovesEveryRevisionAndStopsBothExportQueues() throws Exception {
        Path shared=Files.createDirectory(temp.resolve("shared"));
        var original=save("A");copy(local("A"),shared);
        var service=new SharedTradeService(repo("A"),local("A"),shared);service.open(original.id(),original.revision());
        var correction=corrected(original,"Bad corrected trade");service.update(correction,original.revision());
        assertEquals(6,exports(shared).size());
        var queue=new SharedFolderSyncService(temp.resolve("A/ledger/trades.sqlite"));
        for(Path file:exports(local("A"))) queue.enqueue(file,shared.resolve(file.getFileName()));
        var b=new TradeHistoryService.Session(local("B").toString(),shared.toString());var record=b.refresh().records().getFirst();
        assertEquals(correction.revision(),record.revision);assertNull(repo("B").committedDraft(original.id()));
        assertEquals(0,deletions("B",shared).delete(record).pending());
        assertTrue(exports(shared).isEmpty());assertFalse(Files.exists(SharedTradeService.documentPath(shared,original.id())));
        assertTrue(b.cached().records().isEmpty());
        assertEquals(0,queue.retry(true),"A stale copy queue must respect shared deletion markers before its station imports them");
        assertEquals(0,SharedTradeService.retrySharedOutputs(shared));assertTrue(exports(shared).isEmpty());
        assertEquals(0,deletions("A",shared).sync().pending());assertTrue(exports(local("A")).isEmpty());
        assertTrue(repo("A").history(local("A")).isEmpty());assertEquals(0,repo("A").retryOutputs(local("A")));
        assertThrows(Exception.class,()->service.update(corrected(correction,"Stale editor"),correction.revision()));
        assertTrue(exports(shared).isEmpty());
    }

    @Test void offlineDeletionPublishesAfterRestartAndRemovesAReappearingCopy() throws Exception {
        Path shared=Files.createDirectory(temp.resolve("shared"));var draft=save("A");copy(local("A"),shared);
        var b=new TradeHistoryService.Session(local("B").toString(),shared.toString());var record=b.refresh().records().getFirst();
        Path offline=temp.resolve("disconnected");Files.move(shared,offline);
        assertTrue(deletions("B",shared).delete(record).pending()>0);
        assertTrue(new TradeHistoryService.Session(local("B").toString(),shared.toString()).cached().records().isEmpty());
        Files.move(offline,shared);
        assertEquals(0,deletions("B",shared).sync().pending());assertTrue(exports(shared).isEmpty());
        assertEquals(0,deletions("A",shared).sync().pending());assertTrue(exports(local("A")).isEmpty());
        var output=TradeApplicationService.outputs(draft,false).getFirst();
        Files.writeString(local("A").resolve(output.name()),output.content());
        Files.writeString(shared.resolve(output.name()),output.content());
        assertEquals(0,deletions("A",shared).sync().pending());
        assertTrue(exports(shared).isEmpty());assertTrue(exports(local("A")).isEmpty());
        assertThrows(IOException.class,()->deletions("A",shared).copyToLocal(shared.resolve(output.name())));
    }

    @Test void csvDeletionFindsItsTradeAndLeavesUnrelatedTradeAndExportsAlone() throws Exception {
        Path shared=Files.createDirectory(temp.resolve("shared"));var bad=save("A");var good=save("A");copy(local("A"),shared);
        String badCsv=TradeApplicationService.outputPrefix(bad,false)+".csv";
        assertEquals(0,deletions("B",shared).deleteFile(shared.resolve(badCsv)).pending());
        assertEquals(3,exports(shared).size());
        assertEquals(0,deletions("A",shared).sync().pending());
        var remaining=TradeHistoryService.loadAll(local("A").toString(),shared.toString());
        assertEquals(1,remaining.size());assertEquals(good.id(),remaining.getFirst().tradeId);
        Path unrelated=temp.resolve("unrelated.csv");Files.writeString(unrelated,"keep");
        assertThrows(IOException.class,()->deletions("A",shared).deleteFile(unrelated));assertEquals("keep",Files.readString(unrelated));
    }

    @Test void malformedLegacyFilesAndTheirQueuedSharedCopiesStayDeleted() throws Exception {
        Path shared=Files.createDirectory(temp.resolve("shared"));Files.createDirectories(local("A"));
        String stem="2026-09-26_01-00-00_bad";
        for(String extension:List.of(".csv",".txt",".json")) Files.writeString(local("A").resolve(stem+extension),"bad data");
        var queue=new SharedFolderSyncService(temp.resolve("A/ledger/trades.sqlite"));
        for(Path file:exports(local("A"))) queue.enqueue(file,shared.resolve(file.getFileName()));
        var session=new TradeHistoryService.Session(local("A").toString(),shared.toString());var original=session.refresh().records().getFirst();
        assertEquals(0,deletions("A",shared).deleteFile(local("A").resolve(stem+".csv")).pending());
        assertEquals(0,queue.retry(true));assertTrue(exports(shared).isEmpty());assertTrue(session.cached().records().isEmpty());
        assertThrows(IOException.class,()->TradeHistoryService.saveLegacyReceipt(original,"Cannot restore",local("A").toString()));
        Files.writeString(local("A").resolve(stem+".csv"),"copy from old computer");
        queue.enqueue(local("A").resolve(stem+".csv"),shared.resolve(stem+".csv"));
        assertEquals(0,queue.retry(true));assertTrue(exports(shared).isEmpty());
    }

    @Test void deletionWaitsForSharedPublicationThenRemovesItOnRetry() throws Exception {
        Path shared=Files.createDirectory(temp.resolve("shared"));var draft=save("A");
        var record=repo("A").history(local("A")).getFirst();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var executor=Executors.newSingleThreadExecutor()) {
            var publisher=executor.submit(()->TradeDeletionService.withSharedLock(shared,()->{
                entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));
                for(var output:TradeApplicationService.outputs(draft,false)) Files.writeString(shared.resolve(output.name()),output.content());return null;
            }));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertTrue(deletions("A",shared).delete(record).pending()>0);
            assertTrue(repo("A").history(local("A")).isEmpty());
            release.countDown();publisher.get(5,TimeUnit.SECONDS);
        } finally { release.countDown(); }
        assertEquals(0,deletions("A",shared).sync().pending());assertTrue(exports(shared).isEmpty());
    }

    @Test void sharedDeletionCannotEscapeTradeDirectory() throws Exception {
        Path shared=Files.createDirectory(temp.resolve("shared"));Path outside=temp.resolve("outside.csv");Files.writeString(outside,"keep");
        String key="receipt:bad.txt";
        var malicious=new JSONObject().put("schema",1).put("key",key).put("files",new org.json.JSONArray().put("../outside.csv"));
        Path directory=Files.createDirectory(shared.resolve(TradeDeletionService.DIRECTORY));
        Files.writeString(directory.resolve(AtomicFiles.hash(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))+".json"),malicious.toString());
        assertTrue(deletions("A",shared).sync().pending()>0);assertEquals("keep",Files.readString(outside));
        assertTrue(new TradeDeletionStore(repo("A")).all().isEmpty());
    }

    @Test void unreadableCompanionsCanBeDeletedAndCachedIdentityStillCancelsTheWholeTrade() throws Exception {
        Path shared=Files.createDirectory(temp.resolve("shared"));var draft=save("A");copy(local("A"),shared);
        var session=new TradeHistoryService.Session(local("B").toString(),shared.toString());
        var record=session.refresh().records().getFirst();
        String stem=TradeApplicationService.outputPrefix(draft,false);
        byte[] broken={(byte)0xc3,(byte)0x28};
        Files.write(shared.resolve(stem+".json"),broken);Files.write(shared.resolve(stem+".txt"),broken);
        assertEquals(0,deletions("B",shared).deleteFile(shared.resolve(stem+".csv")).pending());
        assertTrue(new TradeDeletionStore(repo("B")).snapshot().keys().contains(record.historyKey()));
        assertTrue(exports(shared).isEmpty());
        Files.createDirectories(local("C"));
        Files.writeString(local("C").resolve("bad.csv"),"broken");
        Files.write(local("C").resolve("bad.json"),broken);Files.write(local("C").resolve("bad.txt"),broken);
        assertEquals(0,deletions("C",null).deleteFile(local("C").resolve("bad.csv")).pending());
        assertTrue(exports(local("C")).isEmpty());
    }

    @Test void olderRevisionsWithoutSharedRevisionDocumentAreAllRemoved() throws Exception {
        Path shared=Files.createDirectory(temp.resolve("shared"));var original=save("A");
        var correction=corrected(original,"Older local correction");
        new TradeApplicationService(repo("A"),local("A")).updateTrade(correction,original.revision());
        copy(local("A"),shared);assertEquals(6,exports(shared).size());
        assertFalse(Files.exists(shared.resolve(SharedTradeService.DIRECTORY)));
        var record=new TradeHistoryService.Session(local("B").toString(),shared.toString()).refresh().records().getFirst();
        assertEquals(correction.revision(),record.revision);
        assertEquals(0,deletions("B",shared).delete(record).pending());
        assertTrue(exports(shared).isEmpty(),"Original workstation need not be online to discover its older exports");
    }
}
