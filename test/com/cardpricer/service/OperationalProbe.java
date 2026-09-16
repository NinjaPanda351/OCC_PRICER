package com.cardpricer.service;

import com.cardpricer.gui.panel.TradePanel;
import com.cardpricer.model.Card;
import com.cardpricer.util.AtomicFiles;
import org.json.JSONObject;
import javax.swing.SwingUtilities;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.atomic.*;

/** Optional local measurement; never included in deterministic tests or the packaged application. */
public final class OperationalProbe {
    public static void main(String[] args) throws Exception {
        Path source=Path.of(args[0]),root=Path.of(System.getProperty("cardpricer.dataDir")),report=Path.of(args[1]);
        Files.createDirectories(root.resolve("cache"));
        Files.copy(source,root.resolve("cache/catalog.ndjson.gz"),StandardCopyOption.REPLACE_EXISTING);
        var memory=ManagementFactory.getMemoryMXBean();
        AtomicLong peak=new AtomicLong();AtomicBoolean running=new AtomicBoolean(true);
        Thread sampler=new Thread(()->{
            while(running.get()){
                peak.accumulateAndGet(memory.getHeapMemoryUsage().getUsed(),Math::max);
                try{Thread.sleep(5);}catch(InterruptedException e){return;}
            }
        },"heap-probe");sampler.setDaemon(true);sampler.start();
        JSONObject result=new JSONObject().put("measuredAt",java.time.Instant.now()).put("java",System.getProperty("java.version"))
                .put("os",System.getProperty("os.name")).put("heapLimitBytes",Runtime.getRuntime().maxMemory())
                .put("catalogBytes",Files.size(source)).put("catalogSha256",AtomicFiles.hash(Files.readAllBytes(source)))
                .put("scope","Existing local cache (legacy metadata), reload with old index retained, synthetic 1000-line Swing draft; no network or POS");
        try {
            var catalog=ScryfallCatalogService.getInstance();
            long start=System.nanoTime();int count=catalog.loadFromDisk();
            result.put("catalogRecords",count).put("catalogLoadMs",(System.nanoTime()-start)/1_000_000);
            result.put("ambiguousLegacyCodesRequiringLookup",catalog.getAmbiguousLegacyKeys());
            System.gc();result.put("loadedHeapBytes",memory.getHeapMemoryUsage().getUsed());
            start=System.nanoTime();catalog.loadFromDisk();
            result.put("catalogReloadMs",(System.nanoTime()-start)/1_000_000);
            SwingUtilities.invokeAndWait(()->{
                TradePanel panel=new TradePanel();
                try {
                    var batching=TradePanel.class.getDeclaredField("batchingCards");batching.setAccessible(true);batching.setBoolean(panel,true);
                    long began=System.nanoTime();
                    for(int i=0;i<1000;i++){
                        Card card=new Card("Probe card "+i,"TST",Integer.toString(i));card.setProviderId("probe-"+i);card.setRarity("rare");card.setPrice("10.00");
                        panel.addFetchedCard(card,"","TST");
                    }
                    batching.setBoolean(panel,false);
                    var summary=TradePanel.class.getDeclaredMethod("refreshSummary");summary.setAccessible(true);summary.invoke(panel);
                    result.put("add1000LinesMs",(System.nanoTime()-began)/1_000_000);
                    long[] samples=new long[20];
                    for(int i=0;i<samples.length;i++){began=System.nanoTime();summary.invoke(panel);samples[i]=(System.nanoTime()-began)/1_000_000;}
                    Arrays.sort(samples);result.put("summaryMedianMs",samples[10]).put("summaryP95Ms",samples[18]);
                    var snapshot=TradePanel.class.getDeclaredMethod("snapshotDraft");snapshot.setAccessible(true);
                    began=System.nanoTime();var draft=(com.cardpricer.model.TradeDraft)snapshot.invoke(panel);draft.settle();
                    result.put("snapshotAndSettle1000LinesMs",(System.nanoTime()-began)/1_000_000);
                }catch(Exception failure){throw new RuntimeException(failure);}
                finally{panel.disposeResources();}
            });
            result.put("sampledPeakHeapBytes",peak.get()).put("passed",true);
        } finally {running.set(false);sampler.interrupt();AtomicFiles.write(report,result.toString(2));}
    }
}
