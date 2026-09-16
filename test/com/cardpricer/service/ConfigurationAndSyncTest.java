package com.cardpricer.service;
import com.cardpricer.model.BountyCard;
import com.cardpricer.model.BuyRateRule;
import com.cardpricer.util.DataMigration;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationAndSyncTest {
    @TempDir Path temp;
    private static JSONObject config(String rate) {
        return new JSONObject("{\"rules\":[{\"thresholdMin\":\"0\",\"creditRate\":\""+rate+"\",\"checkRate\":\"0.4\"}],\"bounties\":[]}");
    }
    @Test void staleLocalWriterIsRejected() throws Exception {
        Path file=temp.resolve("rates.json");
        var first=new RateConfigurationRepository(file); var second=new RateConfigurationRepository(file);
        first.save("",config("0.5"));
        assertThrows(java.io.IOException.class,()->second.save("",config("0.8")));
        assertEquals("0.5",first.read().getJSONArray("rules").getJSONObject(0).getString("creditRate"));
    }
    @Test void staleSharedVersionCannotOverwritePendingLocalRates() throws Exception {
        var repository=new RateConfigurationRepository(temp.resolve("rates.json"));
        var first=repository.save("",config("0.5"));
        Path shared=temp.resolve("shared.json"); assertEquals("synced",repository.sync(shared));
        repository.save(first.getString("revision"),config("0.8"));
        Files.writeString(shared,config("0.4").put("revision","older").toString());
        assertTrue(repository.sync(shared).startsWith("conflict"));
        assertEquals("0.8",repository.read().getJSONArray("rules").getJSONObject(0).getString("creditRate"));
    }
    @Test void saveBountyDoesNotEraseRulesFromAnotherInstance() throws Exception {
        Path file=temp.resolve("rates.json");
        var first=new BuyRateService(file);var second=new BuyRateService(file);
        first.saveRules(List.of(new BuyRateRule(BigDecimal.ZERO,new BigDecimal("0.9"),new BigDecimal("0.7"))));
        assertThrows(java.io.UncheckedIOException.class,()->second.saveBounties(List.of(new BountyCard("Bounty",BigDecimal.ONE,BigDecimal.ONE))));
        var loaded=new BuyRateService(file);
        assertEquals(new BigDecimal("90.00"),loaded.computePayout("TST","1","Card",new BigDecimal("100")).creditPayout());
    }
    @Test void reviewedSharedChoiceMigratesLegacyAndPreservesBothOriginals() throws Exception {
        Path local=temp.resolve("rates.json"),shared=temp.resolve("shared.json");
        var repository=new RateConfigurationRepository(local);
        repository.save("",config("0.8"));Files.writeString(shared,config("0.6").toString());
        String oldLocal=Files.readString(local),oldShared=Files.readString(shared);
        repository.resolve(repository.review(shared),RateConfigurationRepository.Choice.SHARED);
        assertEquals(Files.readString(local),Files.readString(shared));
        assertEquals("0.6",repository.read().getJSONArray("rules").getJSONObject(0).getString("creditRate"));
        assertFalse(repository.read().getString("revision").isBlank());
        assertEquals(oldLocal,Files.readString(local.resolveSibling("rates.json.previous")));
        assertEquals(oldShared,Files.readString(shared.resolveSibling("shared.json.previous")));
    }
    @Test void changedReviewRejectsReplacementAndRetainsNewerWriter() throws Exception {
        Path shared=temp.resolve("shared.json");var repository=new RateConfigurationRepository(temp.resolve("rates.json"));
        repository.save("",config("0.8"));repository.sync(shared);
        var review=repository.review(shared);
        String newer=config("0.9").put("revision","another-writer").toString();Files.writeString(shared,newer);
        assertThrows(java.io.IOException.class,()->repository.resolve(review,RateConfigurationRepository.Choice.LOCAL));
        assertEquals(newer,Files.readString(shared));
    }
    @Test void reusedRevisionWithDifferentContentIsAConflict() throws Exception {
        Path shared=temp.resolve("shared.json");var repository=new RateConfigurationRepository(temp.resolve("rates.json"));
        repository.save("",config("0.8"));repository.sync(shared);
        JSONObject changed=new JSONObject(Files.readString(shared));changed.getJSONArray("rules").getJSONObject(0).put("creditRate","0.9");
        Files.writeString(shared,changed.toString());assertTrue(repository.sync(shared).startsWith("conflict"));
        repository.resolve(repository.review(shared),RateConfigurationRepository.Choice.LOCAL);
        assertEquals("0.8",new JSONObject(Files.readString(shared)).getJSONArray("rules").getJSONObject(0).getString("creditRate"));
    }
    @Test void quotedBountyCsvIsParsedAndValidated() throws Exception {
        Path csv=temp.resolve("bounties.csv");Files.writeString(csv,"CARD NAME,CREDIT PERCENT,CHECK PERCENT\n\"Name, with comma\",80,60\n");
        var service=new BuyRateService(temp.resolve("rates.json"));
        assertEquals("Name, with comma",service.parseBountyCsv(csv.toFile()).getFirst().cardName);
        Files.writeString(csv,"Bad,101,40\n");assertThrows(java.io.IOException.class,()->service.parseBountyCsv(csv.toFile()));
    }
    @Test void legacyBountyCsvRestoresCommasAndMatchesTheRealCardName() throws Exception {
        Path csv=temp.resolve("bounties.csv");
        Files.writeString(csv,"CARD NAME,CREDIT PERCENT,CHECK PERCENT\nOmnath; Locus of Creation,80,60\nName; with; commas,75,55\n");
        var service=new BuyRateService(temp.resolve("rates.json"));
        var bounties=service.parseBountyCsv(csv.toFile());
        assertEquals("Omnath, Locus of Creation",bounties.getFirst().cardName);
        assertEquals("Name, with, commas",bounties.getLast().cardName);
        service.saveBounties(bounties);
        assertEquals(new BigDecimal("80.00"),service.computePayout("ZNR","232","Omnath, Locus of Creation",new BigDecimal("100")).creditPayout());
    }
    @Test void exportedBountyCsvPreservesPunctuationAndRatesOnImport() throws Exception {
        Path csv=temp.resolve("bounties.csv");
        var service=new BuyRateService(temp.resolve("rates.json"));
        var names=List.of("Omnath, Locus of Creation","Name, with, commas","Name \"with quotes\"; and comma, too","Éowyn, Shieldmaiden","#A name\nwith a newline");
        var bounties=names.stream().map(name->new BountyCard(name,new BigDecimal("0.805"),new BigDecimal("0.605"))).toList();
        service.exportBountyCsv(csv.toFile(),bounties);
        assertTrue(Files.readString(csv).contains("\"Omnath, Locus of Creation\""));
        var imported=service.parseBountyCsv(csv.toFile());
        assertEquals(names,imported.stream().map(bounty->bounty.cardName).toList());
        for(var bounty:imported) {
            assertEquals(0,new BigDecimal("0.805").compareTo(bounty.creditRate));
            assertEquals(0,new BigDecimal("0.605").compareTo(bounty.checkRate));
        }
    }
    @Test void sameSizeSharedConflictIsDetectedAcrossRestart() throws Exception {
        Path source=temp.resolve("source.txt"),destination=temp.resolve("shared.txt"),db=temp.resolve("sync.sqlite");
        Files.writeString(source,"first");Files.writeString(destination,"other");
        var sync=new SharedFolderSyncService(db);sync.enqueue(source,destination);
        assertEquals(1,new SharedFolderSyncService(db).retry(true));
        assertEquals("other",Files.readString(destination));
    }
    @Test void disconnectedShareRetriesWithOneOutput() throws Exception {
        Path source=temp.resolve("source.txt"),shared=temp.resolve("share"),db=temp.resolve("sync.sqlite");
        Files.writeString(source,"receipt");var sync=new SharedFolderSyncService(db);sync.enqueue(source,shared.resolve("trade.txt"));
        assertEquals(1,sync.retry(true));Files.createDirectory(shared);
        assertEquals(0,new SharedFolderSyncService(db).retry(true));assertEquals(0,sync.retry(true));
        assertEquals("receipt",Files.readString(shared.resolve("trade.txt")));
    }
    @Test void migrationRestartsAndPreservesOriginalsAndNestedFiles() throws Exception {
        Path source=temp.resolve("old"),destination=temp.resolve("new");
        Files.createDirectories(source.resolve("nested/deep"));Files.createDirectories(destination);
        Files.writeString(source.resolve("root.txt"),"root");Files.writeString(source.resolve("nested/deep/card.txt"),"card");
        Files.writeString(destination.resolve("root.txt"),"root");
        DataMigration.migrate(source,destination);DataMigration.migrate(source,destination);
        assertEquals("card",Files.readString(destination.resolve("nested/deep/card.txt")));
        assertTrue(Files.exists(source.resolve("root.txt")));assertTrue(Files.exists(destination.resolve("migration-v1.json")));
    }
}
