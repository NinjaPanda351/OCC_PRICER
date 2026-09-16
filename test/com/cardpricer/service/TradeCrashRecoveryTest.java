package com.cardpricer.service;

import com.cardpricer.model.TradeDraft;
import org.json.JSONObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class TradeCrashRecoveryTest {
    @TempDir Path temp;

    @ParameterizedTest
    @ValueSource(strings={"beforeCommit", "afterCommit", "afterOutputWrite"})
    void interruptedProcessRecoversOneTradeAndItsExactOutputs(String boundary) throws Exception {
        TradeDraft draft=TradeRepositoryTest.draft("TST");
        Path input=temp.resolve("draft.json"), database=temp.resolve("ledger.sqlite"),output=temp.resolve("outputs");
        Files.writeString(input,draft.toJson().toString());
        String executable=System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java=Path.of(System.getProperty("java.home"),"bin",executable);
        Process process=new ProcessBuilder(java.toString(),"--enable-native-access=ALL-UNNAMED","-cp",
                System.getProperty("java.class.path"),CrashChild.class.getName(),temp.toString(),boundary)
                .redirectErrorStream(true).redirectOutput(temp.resolve("child.log").toFile()).start();
        try {
            assertTrue(process.waitFor(20,TimeUnit.SECONDS),"Crash probe timed out");
            assertEquals(71,process.exitValue(),()->readLog(temp.resolve("child.log")));
        } finally { if(process.isAlive())process.destroyForcibly(); }
        var repository=new TradeRepository(database);
        boolean committed=!boundary.equals("beforeCommit");
        assertEquals(committed,repository.isCommitted(draft.id()));
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+database);var statement=connection.createStatement();
            var rows=statement.executeQuery("SELECT COUNT(*) FROM drafts")) {
            assertTrue(rows.next());assertEquals(committed?0:1,rows.getInt(1));
        }
        var application=new TradeApplicationService(repository,output);
        assertEquals(0,application.finalizeTrade(draft));
        assertEquals(0,application.finalizeTrade(draft));
        repository.saveDraft(draft); // A late autosave must never resurrect an approved draft.
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+database);var statement=connection.createStatement()) {
            try(var rows=statement.executeQuery("SELECT COUNT(*) FROM drafts")){assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
            try(var rows=statement.executeQuery("SELECT COUNT(*) FROM trades")){assertTrue(rows.next());assertEquals(1,rows.getInt(1));}
            try(var rows=statement.executeQuery("SELECT name,content FROM jobs")) {
                while(rows.next())assertEquals(rows.getString("content"),Files.readString(output.resolve(rows.getString("name"))));
            }
        }
        assertTrue(repository.pending().isEmpty());
        try(var files=Files.list(output)){assertEquals(3,files.count());}
    }
    private static String readLog(Path path){try{return Files.readString(path);}catch(Exception e){return e.toString();}}

    public static final class CrashChild {
        public static void main(String[] args) throws Exception {
            Path root=Path.of(args[0]);
            var draft=TradeDraft.fromJson(new JSONObject(Files.readString(root.resolve("draft.json"))));
            var repository=new TradeRepository(root.resolve("ledger.sqlite"),boundary->{
                if(boundary.equals(args[1]))Runtime.getRuntime().halt(71);
            });
            repository.saveDraft(draft);
            new TradeApplicationService(repository,root.resolve("outputs")).finalizeTrade(draft);
            throw new IllegalStateException("Crash boundary was not reached");
        }
    }
}
