package com.cardpricer.service;

import com.cardpricer.model.TradeDraft;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SharedTradeServiceTest {
    @TempDir Path temp;
    private Path shared() throws Exception { return Files.createDirectories(temp.resolve("shared")); }
    private Path outputs(String station) { return temp.resolve(station).resolve("trades"); }
    private TradeRepository repo(String station) throws Exception { return TradeHistoryService.repository(outputs(station).toString()); }
    private SharedTradeService service(String station) throws Exception {
        return new SharedTradeService(repo(station),outputs(station),shared(),"Staff "+station,station,ignored -> {});
    }
    private TradeDraft original() throws Exception {
        TradeDraft draft=TradeRepositoryTest.draft("TST");
        new TradeApplicationService(repo("A"),outputs("A")).finalizeTrade(draft);
        try (var files=Files.list(outputs("A"))) {
            for (Path file:files.toList()) Files.copy(file,shared().resolve(file.getFileName()));
        }
        return draft;
    }
    private static TradeDraft corrected(TradeDraft original, String customer) {
        return new TradeDraft(original.id(),original.revision()+1,original.trader(),customer,original.identification(),
                original.checkNumber(),original.payment(),original.credit(),original.check(),original.lines(),original.quotedAt(),original.rateRevision());
    }

    @Test void otherWorkstationEditsCompleteTradeAndOriginalWorkstationCanEditAgain() throws Exception {
        var original=original();
        var record=TradeHistoryService.loadAll(outputs("B").toString(),shared().toString()).getFirst();
        assertNull(repo("B").committedDraft(original.id()));
        var opened=TradeHistoryService.openForEditing(record,outputs("B").toString(),shared().toString());
        assertEquals(original,opened); // Includes printing, saved rates, condition, quantities and payment.
        repo("B").setInventoried(record,true);
        var corrected=corrected(opened,"Corrected at B");
        assertEquals(0,new TradeApplicationService(repo("B"),outputs("B"),shared()).updateTrade(corrected,opened.revision()));
        var history=TradeHistoryService.loadAll(outputs("A").toString(),shared().toString());
        assertEquals(1,history.size());
        assertEquals("Corrected at B",history.getFirst().customerName);
        assertFalse(TradeHistoryService.loadAll(outputs("B").toString(),shared().toString()).getFirst().inventoried);
        assertEquals(corrected,service("A").open(original.id(),corrected.revision()));
        var again=corrected(corrected,"Corrected again at A");
        assertEquals(0,service("A").update(again,corrected.revision()));
        assertEquals(again,service("B").open(original.id(),again.revision()));
        assertEquals(3,repo("A").versions(original.id()).size());
        assertEquals(3,repo("B").versions(original.id()).size());
        assertTrue(Files.readString(shared().resolve(TradeApplicationService.outputPrefix(original,false)+".txt")).contains("Customer Name: Customer"));
    }

    @Test void staleSaveCannotOverwriteWinnerAndExactRetryDoesNotAddRevision() throws Exception {
        var original=original();service("A").open(original.id(),1);service("B").open(original.id(),1);
        var winner=corrected(original,"Winner");
        service("B").update(winner,1);
        assertThrows(IOException.class,() -> service("A").update(corrected(original,"Stale"),1));
        assertEquals(original,repo("A").committedDraft(original.id()));
        assertEquals(0,service("B").update(winner,1));
        assertEquals(2,repo("B").versions(original.id()).size());
        String log=SharedTradeService.revisionHistory(repo("B"),shared(),original.id());
        assertTrue(log.contains("Staff B"));assertTrue(log.contains("Workstation: B"));
        assertTrue(log.contains("Customer: Customer → Winner"));
    }

    @Test void simultaneousSavesAcceptExactlyOneCorrection() throws Exception {
        var original=original();service("A").open(original.id(),1);service("B").open(original.id(),1);
        var a=service("A");var b=service("B");
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try (var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(() -> race(a,corrected(original,"A wins"),ready,start));
            var second=executor.submit(() -> race(b,corrected(original,"B wins"),ready,start));
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            assertEquals(1,first.get(10,TimeUnit.SECONDS)+second.get(10,TimeUnit.SECONDS));
        }
        var document=new JSONObject(Files.readString(SharedTradeService.documentPath(shared(),original.id())));
        assertEquals(2,document.getJSONArray("versions").length());
    }
    private static int race(SharedTradeService service,TradeDraft draft,CountDownLatch ready,CountDownLatch start) throws Exception {
        ready.countDown();assertTrue(start.await(5,TimeUnit.SECONDS));
        try { service.update(draft,1);return 1; }
        catch (IOException conflict) {
            assertTrue(conflict.getMessage().contains("changed") || conflict.getMessage().contains("Another workstation"));return 0;
        }
    }

    @Test void sharedCommitSurvivesFailureBeforeLocalCacheAndExports() throws Exception {
        var original=original();service("B").open(original.id(),1);
        var correction=corrected(original,"Saved before interruption");
        var interrupted=new SharedTradeService(repo("B"),outputs("B"),shared(),"Staff B","B",point -> {
            if (point.equals("afterSharedCommit")) throw new IllegalStateException("interrupted");
        });
        assertThrows(IllegalStateException.class,() -> interrupted.update(correction,1));
        assertEquals(original,repo("B").committedDraft(original.id()));
        Path receipt=shared().resolve(TradeApplicationService.outputPrefix(correction,true)+".txt");
        assertFalse(Files.exists(receipt));
        var history=TradeHistoryService.loadAll(outputs("C").toString(),shared().toString());
        assertEquals(1,history.size());assertEquals(2,history.getFirst().revision);
        assertTrue(TradeHistoryService.receiptContent(history.getFirst(),outputs("C").toString()).contains(correction.customer()));
        assertEquals(0,SharedTradeService.retrySharedOutputs(shared()));assertTrue(Files.exists(receipt));
        assertEquals(0,service("B").update(correction,1));
        assertEquals(correction,repo("B").committedDraft(original.id()));
        assertEquals(correction,service("C").open(original.id(),2));
    }

    @Test void offlineOrChangedShareCannotFallBackToUncheckedLocalSave() throws Exception {
        var original=original();service("B").open(original.id(),1);
        var correction=corrected(original,"Unsaved");
        assertThrows(IllegalStateException.class,() -> new TradeApplicationService(repo("B"),outputs("B")).updateTrade(correction,1));
        Path different=Files.createDirectory(temp.resolve("different-share"));
        assertThrows(IOException.class,() -> new TradeApplicationService(repo("B"),outputs("B"),different).updateTrade(correction,1));
        Path offline=temp.resolve("disconnected-share");
        assertThrows(IOException.class,() -> new TradeApplicationService(repo("B"),outputs("B"),offline).updateTrade(correction,1));
        assertFalse(Files.exists(offline));
        assertEquals(original,repo("B").committedDraft(original.id()));
    }

    @Test void heldLockGivesBusyMessageAndDoesNotWriteCorrection() throws Exception {
        var original=original();service("A").open(original.id(),1);
        Path lock=shared().resolve(SharedTradeService.DIRECTORY).resolve(original.id()+".lock");
        try (var channel=FileChannel.open(lock,StandardOpenOption.WRITE);var held=channel.lock()) {
            var error=assertThrows(IOException.class,() -> service("B").update(corrected(original,"Busy"),1));
            assertTrue(error.getMessage().contains("Another workstation"));
        }
        assertEquals(1,SharedTradeService.history(shared()).getFirst().revision);
    }

    @Test void conflictingSameRevisionCopiesAreRetainedAndBlockEditing() throws Exception {
        var original=original();
        var conflicting=new JSONObject(original.toJson().toString()).put("customer","Conflicting copy");
        Path file=shared().resolve("conflict.json");Files.writeString(file,conflicting.toString());
        assertThrows(IOException.class,() -> service("B").open(original.id(),1));
        assertEquals(conflicting.toString(),Files.readString(file));
        assertNull(repo("B").committedDraft(original.id()));
    }

    @Test void malformedManifestAndOutputTraversalFailClosed() throws Exception {
        var original=original();service("A").open(original.id(),1);
        Path file=SharedTradeService.documentPath(shared(),original.id());
        JSONObject doc=new JSONObject(Files.readString(file));
        doc.getJSONArray("versions").getJSONObject(0).getJSONArray("outputs").getJSONObject(0).put("name","../escape.txt");
        Files.writeString(file,doc.toString());
        assertThrows(IOException.class,() -> service("B").open(original.id(),1));
        assertThrows(IOException.class,() -> SharedTradeService.retrySharedOutputs(shared()));
        assertFalse(Files.exists(temp.resolve("escape.txt")));
        Files.writeString(file,"{broken");
        assertThrows(IOException.class,() -> service("B").update(corrected(original,"No"),1));
    }

    @Test void missingSharedHistoryCannotRecreateAStaleRevision() throws Exception {
        var original=original();service("B").open(original.id(),1);
        Files.delete(SharedTradeService.documentPath(shared(),original.id()));
        var error=assertThrows(IOException.class,() -> service("B").update(corrected(original,"Stale"),1));
        assertTrue(error.getMessage().contains("history is missing"));
        assertEquals(original,repo("B").committedDraft(original.id()));
    }

    @Test void revisionFromAnOlderAppCannotSilentlyReplaceSharedHistory() throws Exception {
        var original=original();service("B").open(original.id(),1);
        var external=corrected(original,"Saved outside shared editing");
        new TradeApplicationService(repo("A"),outputs("A")).updateTrade(external,1);
        try (var files=Files.list(outputs("A"))) {
            for (Path file:files.toList()) if (!Files.exists(shared().resolve(file.getFileName())))
                Files.copy(file,shared().resolve(file.getFileName()));
        }
        var error=assertThrows(IOException.class,() -> service("B").update(corrected(original,"No"),1));
        assertTrue(error.getMessage().contains("outside shared editing"));
        assertEquals(1,SharedTradeService.history(shared()).getFirst().revision);
    }

    @ParameterizedTest @ValueSource(strings={"beforeSharedCommit","afterSharedCommit"})
    void processCrashReleasesSharedLockAndRecoversCommittedRevision(String point) throws Exception {
        var original=original();service("B").open(original.id(),1);
        var correction=corrected(original,"Process crash correction");
        Files.writeString(temp.resolve("correction.json"),correction.toJson().toString());
        String executable=System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java=Path.of(System.getProperty("java.home"),"bin",executable);
        Path log=temp.resolve("crash.log");
        Process child=new ProcessBuilder(java.toString(),"--enable-native-access=ALL-UNNAMED","-cp",System.getProperty("java.class.path"),
                CrashChild.class.getName(),temp.toString(),point).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(child.waitFor(20,TimeUnit.SECONDS),"Crash probe timed out");
            assertEquals(71,child.exitValue(),() -> { try { return Files.readString(log); } catch (Exception e) { return e.toString(); } });
        } finally { if (child.isAlive()) child.destroyForcibly(); }
        boolean committed=point.equals("afterSharedCommit");
        assertEquals(original,repo("B").committedDraft(original.id()));
        assertEquals(committed ? correction : original,service("C").open(original.id(),committed ? 2 : 1));
        assertEquals(0,service("B").update(correction,1));
        assertEquals(correction,repo("B").committedDraft(original.id()));
    }
    public static class CrashChild {
        public static void main(String[] args) throws Exception {
            Path root=Path.of(args[0]),output=root.resolve("B/trades");
            var repo=TradeHistoryService.repository(output.toString());
            TradeDraft correction=TradeDraft.fromJson(new JSONObject(Files.readString(root.resolve("correction.json"))));
            new SharedTradeService(repo,output,root.resolve("shared"),"Staff B","B",point -> {
                if (point.equals(args[1])) Runtime.getRuntime().halt(71);
            }).update(correction,1);
        }
    }
}
