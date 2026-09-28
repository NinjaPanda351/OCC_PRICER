package com.cardpricer.service;

import com.cardpricer.model.TradeRecord;
import com.cardpricer.util.AtomicFiles;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static com.cardpricer.service.TradeDeletionStore.Deletion;

/** Deletions win over export retries and are retried across offline workstations. */
public final class TradeDeletionService {
    static final Object LOCAL_LOCK=new Object();
    static final String DIRECTORY=".trade-deletions";
    private final TradeRepository repository;
    private final TradeDeletionStore store;
    private final Path local,shared;
    public TradeDeletionService(TradeRepository repository,Path local,Path shared) {
        this.repository=repository;store=new TradeDeletionStore(repository);
        this.local=local.toAbsolutePath().normalize();this.shared=shared==null ? null : shared.toAbsolutePath().normalize();
    }
    public record Result(int pending) {}
    interface Operation<T> { T run() throws Exception; }

    /** Serialize shared file publication with deletion, including between separate workstations. */
    static <T> T withSharedLock(Path shared,Operation<T> operation) throws Exception {
        if(!Files.isDirectory(shared)) throw new IOException("Shared trades folder unavailable");
        Path directory=shared.resolve(DIRECTORY);Files.createDirectories(directory);
        try(var channel=FileChannel.open(directory.resolve("files.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE)) {
            try(var lock=channel.tryLock()) {
                if(lock==null) throw new IOException("Shared trade files are busy; retry shortly");
                return operation.run();
            } catch(OverlappingFileLockException busy) { throw new IOException("Shared trade files are busy; retry shortly",busy); }
        }
    }
    private static String hash(String text) { return AtomicFiles.hash(text.getBytes(StandardCharsets.UTF_8)); }
    private static Path marker(Path shared,String key) { return shared.resolve(DIRECTORY).resolve(hash(key)+".json"); }
    static boolean isSharedDeleted(Path shared,String key,String name) throws IOException {
        if(key!=null && Files.exists(marker(shared,key))) return true;
        return name!=null && Files.exists(shared.resolve(DIRECTORY).resolve("files").resolve(hash(name.toLowerCase(Locale.ROOT))));
    }
    private static List<Deletion> readShared(Path shared) throws IOException {
        Path directory=shared.resolve(DIRECTORY);
        if(Files.notExists(directory)) return List.of();
        var result=new ArrayList<Deletion>();
        try(var files=Files.newDirectoryStream(directory,"*.json")) {
            for(Path file:files) {
                try {
                    var deletion=Deletion.parse(Files.readString(file));
                    if(!file.getFileName().equals(marker(shared,deletion.key()).getFileName())) throw new IOException("Deletion filename does not match its trade");
                    result.add(deletion);
                } catch(RuntimeException invalid) { throw new IOException("Invalid trade deletion record: "+file.getFileName(),invalid); }
            }
        }
        return result;
    }
    static void addCompanions(Set<String> names,String name) {
        TradeDeletionStore.validateName(name);
        names.add(name);
        String stem=name.substring(0,name.lastIndexOf('.'));
        for(String extension:List.of(".csv",".txt",".json",".pdf")) names.add(stem+extension);
    }
    private static Path child(Path directory,String name) {
        TradeDeletionStore.validateName(name);
        Path root=directory.toAbsolutePath().normalize(),file=root.resolve(name).normalize();
        if(!root.equals(file.getParent())) throw new IllegalArgumentException("Trade file is outside its folder");
        return file;
    }

    public Result delete(TradeRecord record) throws Exception {
        var names=new TreeSet<String>();addCompanions(names,Path.of(record.filename).getFileName().toString());
        return delete(new Deletion(record.historyKey(),names));
    }
    public Result deleteFile(Path file) throws Exception {
        file=file.toAbsolutePath().normalize();
        if(!local.equals(file.getParent()) && (shared==null || !shared.equals(file.getParent())))
            throw new IOException("This is not a trade file in the configured trades folders");
        String name=file.getFileName().toString();
        var names=new TreeSet<String>();addCompanions(names,name);
        UUID id=repository.tradeForOutput(name);
        String stem=name.substring(0,name.lastIndexOf('.'));
        if(id==null) {
            Path json=file.resolveSibling(stem+".json");
            if(Files.isRegularFile(json)) try { id=UUID.fromString(new JSONObject(Files.readString(json)).getString("id")); }
            catch(IOException | RuntimeException invalid) { /* Bad exports still need to be deletable. */ }
        }
        if(id==null) {
            Path receipt=file.resolveSibling(stem+".txt");
            if(Files.isRegularFile(receipt)) try { id=TradeHistoryService.readReceipt(receipt).record().tradeId; }
            catch(IOException invalid) { /* Deletion must not require a readable receipt. */ }
        }
        if(id==null && shared!=null && Files.isDirectory(shared.resolve(SharedTradeService.DIRECTORY))) {
            try(var documents=Files.newDirectoryStream(shared.resolve(SharedTradeService.DIRECTORY),"*.json")) {
                for(Path document:documents) try {
                    var json=new JSONObject(Files.readString(document));
                    if(documentNames(json).contains(name)) { id=UUID.fromString(json.getString("id"));break; }
                } catch(IOException | RuntimeException invalid) { /* Preserve unrelated malformed documents. */ }
            } catch(IOException unavailable) { /* Queue deletion using the identity available locally. */ }
        }
        return delete(new Deletion(id==null ? "receipt:"+stem+".txt" : "trade:"+id,names));
    }
    private Result delete(Deletion deletion) throws Exception {
        String folder=shared==null ? "" : shared.toString();
        if(deletion.tradeId()!=null) {
            String bound=repository.sharedSource(deletion.tradeId());
            if(!bound.isBlank()) {
                if(shared!=null && !Path.of(bound).toAbsolutePath().normalize().equals(shared))
                    throw new IOException("Restore this trade's Shared Trades Folder in Preferences before deleting it");
                folder=bound;
            }
        }
        store.record(deletion,folder); // Commit intent before removing any files.
        return sync();
    }

    /** Called before exports and periodically even when the Trade panel is closed. */
    public Result sync() throws Exception {
        boolean sharedIsLocal=shared!=null && shared.equals(local);
        int pending=sharedIsLocal ? 0 : cleanupLocal();
        List<TradeDeletionStore.Saved> saved=store.all();
        if(shared==null) return new Result(pending+(int)saved.stream().filter(s->!s.sharedFolder().isBlank()).count());
        try {
            pending+=withSharedLock(shared,()->{
                for(var deletion:readShared(shared)) store.record(deletion,shared.toString());
                int failures=0;
                for(var entry:store.all()) {
                    if(!entry.sharedFolder().isBlank() && !Path.of(entry.sharedFolder()).equals(shared)) continue;
                    var deletion=entry.deletion();
                    Path marker=marker(shared,deletion.key());
                    if(deletion.tradeId()!=null && !Files.exists(marker)) deletion=discoverOlderExports(shared,deletion);
                    Path document=deletion.tradeId()==null ? null : SharedTradeService.documentPath(shared,deletion.tradeId());
                    if(document!=null && Files.isRegularFile(document)) {
                        try {
                            var json=new JSONObject(Files.readString(document));
                            if(!deletion.tradeId().toString().equals(json.getString("id"))) throw new IOException("Shared trade identity mismatch");
                            deletion=deletion.merge(new Deletion(deletion.key(),documentNames(json)));
                        } catch(RuntimeException invalid) { /* Delete a malformed saved trade using its known identity and files. */ }
                    }
                    deletion=store.record(deletion,shared.toString());
                    String text=deletion.json().toString();
                    if(!Files.exists(marker) || !Deletion.parse(Files.readString(marker)).equals(deletion)) AtomicFiles.write(marker,text);
                    Path fileMarkers=shared.resolve(DIRECTORY).resolve("files");Files.createDirectories(fileMarkers);
                    for(String name:deletion.files()) {
                        Path blocked=fileMarkers.resolve(hash(name.toLowerCase(Locale.ROOT)));
                        if(!Files.exists(blocked)) AtomicFiles.write(blocked,"");
                    }
                    failures+=removeFiles(shared,deletion);
                }
                return failures;
            });
        } catch(IOException failure) {
            // The intent is durable locally; subsequent background passes retry publication/cleanup.
            pending++;
        }
        return new Result(pending+(sharedIsLocal ? 0 : cleanupLocal()));
    }
    private int cleanupLocal() throws Exception {
        synchronized(LOCAL_LOCK) {
            int pending=0;
            for(var entry:store.all()) {
                var deletion=entry.deletion();
                Path document=deletion.tradeId()==null ? null : SharedTradeService.documentPath(local,deletion.tradeId());
                if(document!=null && Files.isRegularFile(document)) try {
                    var json=new JSONObject(Files.readString(document));
                    if(deletion.tradeId().toString().equals(json.getString("id")))
                        deletion=store.record(deletion.merge(new Deletion(deletion.key(),documentNames(json))),entry.sharedFolder());
                } catch(IOException | RuntimeException invalid) { /* Known files can still be removed. */ }
                pending+=removeFiles(local,deletion);
            }
            return pending;
        }
    }
    private static Set<String> documentNames(JSONObject document) {
        var names=new TreeSet<String>();
        for(Object version:document.getJSONArray("versions")) for(Object output:((JSONObject)version).getJSONArray("outputs"))
            addCompanions(names,((JSONObject)output).getString("name"));
        return names;
    }
    /** Older exports can have several revisions without a .trade-revisions document. */
    private static Deletion discoverOlderExports(Path directory,Deletion deletion) throws IOException {
        var names=new TreeSet<>(deletion.files());
        try(var files=Files.newDirectoryStream(directory)) {
            for(Path file:files) {
                String name=file.getFileName().toString();
                if(!Files.isRegularFile(file) || !(name.endsWith(".json") || name.endsWith(".txt"))) continue;
                try {
                    String id=name.endsWith(".json") ? new JSONObject(Files.readString(file)).optString("id")
                            : Objects.toString(TradeHistoryService.readReceipt(file).record().tradeId,"");
                    if(deletion.tradeId().toString().equals(id)) addCompanions(names,name);
                } catch(IOException | RuntimeException invalid) { /* Unrelated corrupt files must not block deletion. */ }
            }
        }
        return new Deletion(deletion.key(),names);
    }
    private static int removeFiles(Path directory,Deletion deletion) {
        int pending=0;
        for(String name:deletion.files()) try { Files.deleteIfExists(child(directory,name)); } catch(IOException failure) { pending++; }
        if(deletion.tradeId()!=null) try { Files.deleteIfExists(SharedTradeService.documentPath(directory,deletion.tradeId())); }
        catch(IOException failure) { pending++; }
        return pending;
    }
    public Set<String> deletedFileNames() throws Exception { return Set.copyOf(store.snapshot().files()); }
    public void copyToLocal(Path source) throws Exception {
        if(shared==null || !source.toAbsolutePath().normalize().getParent().equals(shared)) throw new IOException("Not a file in the shared trades folder");
        withSharedLock(shared,()->{
            synchronized(LOCAL_LOCK) {
                String name=source.getFileName().toString();
                if(store.contains("",name) || isSharedDeleted(shared,null,name)) throw new IOException("This trade was deleted");
                Files.createDirectories(local);
                Files.copy(source,child(local,name),StandardCopyOption.REPLACE_EXISTING);
            }
            return null;
        });
    }
}
