package com.cardpricer.service;

import com.cardpricer.model.InventoryStatusChange;
import com.cardpricer.util.AppDataDirectory;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Exchanges immutable status operations. The shared folder never contains a live database. */
public final class InventoryStatusSyncService {
    static final String DIRECTORY="pos-inventory-status";
    private final TradeRepository repository;
    public InventoryStatusSyncService(TradeRepository repository) { this.repository=repository; }
    public record Result(int imported, int published, int errors) {}

    public Result sync(Path sharedFolder) throws Exception {
        if (!Files.isDirectory(sharedFolder)) throw new IOException("Shared trades folder unavailable");
        Path directory=sharedFolder.resolve(DIRECTORY);
        try { Files.createDirectory(directory); }
        catch (FileAlreadyExistsException exists) { if (!Files.isDirectory(directory)) throw exists; }
        Map<UUID,InventoryStatusChange> known=new HashMap<>();
        for (var change:repository.inventoryChanges()) known.put(change.id(),change);
        Set<UUID> present=new HashSet<>();
        int imported=0,published=0,errors=0;
        try (var files=Files.list(directory)) {
            for (Path file:files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                InventoryStatusChange change;
                try {
                    change=read(file);
                    if (!file.getFileName().toString().equals(change.id()+".json"))
                        throw new IOException("Status filename does not match its ID");
                    var existing=known.get(change.id());
                    if (existing!=null && !existing.equals(change)) throw new IOException("Conflicting status contents");
                } catch (IOException | RuntimeException invalid) { errors++;continue; }
                if (!known.containsKey(change.id()) && repository.importInventoryChange(change)) imported++;
                known.put(change.id(),change);present.add(change.id());
            }
        }
        // Reload so a checkbox changed during the read phase can be published in this pass.
        for (var change:repository.inventoryChanges()) {
            if (present.contains(change.id())) continue;
            try { publish(directory,change);published++; }
            catch (IOException | RuntimeException failure) { errors++; }
        }
        return new Result(imported,published,errors);
    }

    private static InventoryStatusChange read(Path file) throws IOException {
        if (Files.size(file)>16_384) throw new IOException("POS status document is too large");
        return InventoryStatusChange.fromJson(new JSONObject(Files.readString(file,StandardCharsets.UTF_8)));
    }

    private static void publish(Path directory,InventoryStatusChange change) throws IOException {
        Path destination=directory.resolve(change.id()+".json");
        if (Files.exists(destination)) {
            if (!read(destination).equals(change)) throw new IOException("Conflicting shared POS status");
            return;
        }
        Path staged=Files.createTempFile(directory,".pos-status-",".tmp");
        try {
            try (FileChannel channel=FileChannel.open(staged,StandardOpenOption.WRITE)) {
                ByteBuffer bytes=StandardCharsets.UTF_8.encode(change.toJson().toString(2));
                while(bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            try { Files.move(staged,destination); }
            catch (FileAlreadyExistsException race) {
                if (!read(destination).equals(change)) throw new IOException("Conflicting shared POS status",race);
            }
        } finally { Files.deleteIfExists(staged); }
    }

    private static final ScheduledExecutorService EXECUTOR=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread=new Thread(r,"pos-status-sync");thread.setDaemon(true);return thread;
    });
    private static final AtomicBoolean STARTED=new AtomicBoolean();
    private static final AtomicBoolean QUEUED=new AtomicBoolean();
    private static final AtomicBoolean RESYNC_REQUESTED=new AtomicBoolean();
    private static final AtomicLong GENERATION=new AtomicLong();
    private static volatile String status="POS status: saved locally";

    /** Application lifetime polling also retries offline changes while History is closed. */
    public static void start() {
        if (STARTED.compareAndSet(false,true)) EXECUTOR.scheduleWithFixedDelay(
                InventoryStatusSyncService::requestSync,0,15,TimeUnit.SECONDS);
    }

    public static void requestSync() {
        if (!QUEUED.compareAndSet(false,true)) { RESYNC_REQUESTED.set(true);return; }
        status="POS status: syncing...";
        EXECUTOR.execute(() -> {
            try {
                String shared=com.cardpricer.gui.panel.PreferencesPanel.getSharedTradesFolder();
                if (shared==null || shared.isBlank()) { status="POS status: local only (no shared folder)";return; }
                status="POS status: syncing...";
                var repository=new TradeRepository(AppDataDirectory.root().toPath().resolve("ledger/trades.sqlite"));
                Result result=new InventoryStatusSyncService(repository).sync(Path.of(shared));
                status=result.errors()==0 ? "POS status: synced with shared folder"
                        : "POS status: "+result.errors()+" shared update(s) need retry or review";
            } catch (Exception failure) {
                status="POS status: saved locally; network sync pending";
            } finally {
                GENERATION.incrementAndGet();QUEUED.set(false);
                if (RESYNC_REQUESTED.getAndSet(false)) requestSync();
            }
        });
    }

    public static String status() { return status; }
    public static long generation() { return GENERATION.get(); }
}
