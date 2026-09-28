package com.cardpricer.service;
import com.cardpricer.util.AtomicFiles;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.UUID;

/** Revision checked local and shared documents; no timestamp freshness guesses. */
public final class RateConfigurationRepository {
    private static final java.util.concurrent.ConcurrentMap<Path,Object> LOCAL_LOCKS=new java.util.concurrent.ConcurrentHashMap<>();
    private final Path local;
    private final Object localMutex;
    public RateConfigurationRepository(Path local) {
        this.local = local.toAbsolutePath().normalize();
        localMutex=LOCAL_LOCKS.computeIfAbsent(this.local, ignored -> new Object());
    }
    public Path path() { return local; }
    public JSONObject read() throws IOException {
        return Files.exists(local) ? new JSONObject(Files.readString(local)) : new JSONObject();
    }
    public synchronized JSONObject save(String expected, JSONObject data) throws IOException {
        synchronized (localMutex) {
            validate(data);
            Files.createDirectories(local.getParent());
            try (FileChannel channel = FileChannel.open(local.resolveSibling(local.getFileName()+".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                JSONObject previous = read();
                if (!previous.optString("revision").equals(expected)) throw new IOException("Rate configuration changed. Reload before saving.");
                JSONObject next = new JSONObject(data.toString()).put("schema",1).put("revision",UUID.randomUUID().toString())
                        .put("parent", previous.optBoolean("pending") ? previous.optString("parent") : previous.optString("revision"))
                        .put("pending",true);
                if (Files.exists(local)) AtomicFiles.write(local.resolveSibling(local.getFileName()+".previous"), previous.toString());
                AtomicFiles.write(local,next.toString(2));
                return next;
            }
        }
    }
    public enum Choice { LOCAL, SHARED }
    /** The exact reviewed bytes are checked again when the operator chooses a version. */
    public record Review(Path shared, String localDocument, String sharedDocument) {}
    public Review review(Path shared) throws IOException {
        return new Review(shared.toAbsolutePath(), Files.exists(local) ? Files.readString(local) : "",
                Files.exists(shared) ? Files.readString(shared) : "");
    }
    public synchronized void resolve(Review review, Choice choice) throws IOException {
        synchronized (localMutex) {
            Path shared = review.shared().toAbsolutePath().normalize();
            if (local.equals(shared)) throw new IOException("Local and shared configuration paths must differ");
            Files.createDirectories(local.getParent());
            try (FileChannel localLock = FileChannel.open(local.resolveSibling(local.getFileName()+".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var heldLocal = localLock.lock();
                 FileChannel sharedLock = FileChannel.open(shared.resolveSibling(shared.getFileName()+".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var heldShared = sharedLock.tryLock()) {
                if (heldShared == null) throw new IOException("Shared rates are busy. Review again when the other save finishes.");
                String here = Files.exists(local) ? Files.readString(local) : "";
                String there = Files.exists(shared) ? Files.readString(shared) : "";
                if (!here.equals(review.localDocument()) || !there.equals(review.sharedDocument()))
                    throw new IOException("Rates changed after this review. Review both versions again.");
                String selected = choice == Choice.LOCAL ? here : there;
                if (selected.isBlank()) throw new IOException("The selected version has no saved rates");
                JSONObject next = new JSONObject(selected);
                validate(next);
                String remoteRevision = "";
                try { if (!there.isBlank()) remoteRevision = new JSONObject(there).optString("revision"); }
                catch (RuntimeException invalidRemote) { /* The original is backed up; a valid local choice can repair it. */ }
                boolean replaceShared = choice == Choice.LOCAL || remoteRevision.isBlank();
                if (replaceShared) next.put("schema", 1).put("revision", UUID.randomUUID().toString())
                        .put("parent", remoteRevision);
                next.put("pending", false);
                // Preserve both originals before replacing either side, including legacy documents.
                if (!here.isBlank()) AtomicFiles.write(local.resolveSibling(local.getFileName()+".previous"), here);
                if (replaceShared && !there.isBlank()) AtomicFiles.write(shared.resolveSibling(shared.getFileName()+".previous"), there);
                if (replaceShared) AtomicFiles.write(shared, next.toString(2));
                AtomicFiles.write(local, next.toString(2));
            } catch (java.nio.channels.OverlappingFileLockException busy) {
                throw new IOException("Shared rates are busy. Review again when the other save finishes.", busy);
            }
        }
    }
    public synchronized String sync(Path shared) throws IOException {
        synchronized (localMutex) {
            shared=shared.toAbsolutePath().normalize();
            if (local.equals(shared)) throw new IOException("Local and shared configuration paths must differ");
            Files.createDirectories(local.getParent());
            try (FileChannel localLock = FileChannel.open(local.resolveSibling(local.getFileName()+".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var heldLocal = localLock.lock();
                 FileChannel sharedLock = FileChannel.open(shared.resolveSibling(shared.getFileName()+".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var heldShared = sharedLock.tryLock()) {
                if (heldShared == null) return "pending sync";
                JSONObject here=read();
                JSONObject there=Files.exists(shared) ? new JSONObject(Files.readString(shared)) : new JSONObject();
                String localRevision=here.optString("revision"), remoteRevision=there.optString("revision");
                if (!there.isEmpty() && remoteRevision.isBlank())
                    return "conflict: older shared rates need migration; click Review shared rates";
                if (here.isEmpty()) {
                    if (!there.isEmpty()) { validate(there); AtomicFiles.write(local,there.put("pending",false).toString(2)); }
                    return "synced";
                }
                if (localRevision.equals(remoteRevision) && !localRevision.isEmpty()) {
                    JSONObject localContent = new JSONObject(here.toString()), sharedContent = new JSONObject(there.toString());
                    localContent.remove("pending"); sharedContent.remove("pending");
                    if (!localContent.similar(sharedContent)) return "conflict: equal revision has different contents; review shared rates";
                    AtomicFiles.write(local,here.put("pending",false).toString(2)); return "synced";
                }
                if (here.optBoolean("pending") && remoteRevision.equals(here.optString("parent"))) {
                    validate(here);
                    if (Files.exists(shared)) AtomicFiles.write(shared.resolveSibling(shared.getFileName()+".previous"),there.toString());
                    AtomicFiles.write(shared,here.put("pending",false).toString(2)); AtomicFiles.write(local,here.toString(2)); return "synced";
                }
                if (!here.optBoolean("pending") && !there.isEmpty()) {
                    // A clean workstation can miss several shared revisions while offline.
                    validate(there);
                    AtomicFiles.write(local.resolveSibling(local.getFileName()+".previous"),here.toString(2));
                    AtomicFiles.write(local,there.put("pending",false).toString(2)); return "synced";
                }
                return "conflict: local and shared rates differ; click Review shared rates";
            } catch (java.nio.channels.OverlappingFileLockException busy) {
                return "pending sync: shared rates are busy; retrying";
            }
        }
    }
    public static void validate(JSONObject data) throws IOException {
        if (data.optInt("schema",1)!=1) throw new IOException("Unsupported rate configuration schema");
        var rules=data.optJSONArray("rules");
        if (rules==null || rules.isEmpty()) throw new IOException("Rate rules are missing");
        java.util.Set<java.math.BigDecimal> thresholds=new java.util.HashSet<>(); boolean catchAll=false;
        try {
            for (Object value:rules) {
                JSONObject rule=(JSONObject)value;
                var item=new com.cardpricer.model.BuyRateRule(rule.getBigDecimal("thresholdMin"),rule.getBigDecimal("creditRate"),rule.getBigDecimal("checkRate"));
                if (!thresholds.add(item.thresholdMin.stripTrailingZeros())) throw new IOException("Duplicate thresholds");
                catchAll |= item.thresholdMin.signum()==0;
            }
            if (!catchAll) throw new IOException("Catch-all rule is missing");
            var bounties=data.optJSONArray("bounties");
            if (bounties!=null) for (Object value:bounties) {
                JSONObject bounty=(JSONObject)value;
                new com.cardpricer.model.BountyCard(bounty.getString("cardName"),bounty.getBigDecimal("creditRate"),bounty.getBigDecimal("checkRate"));
            }
        } catch (RuntimeException e) { throw new IOException("Invalid rate configuration",e); }
    }
}
