package com.cardpricer.service;

import com.cardpricer.model.TradeDraft;
import com.cardpricer.model.TradeRecord;
import com.cardpricer.util.AtomicFiles;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/** Shared documents are authoritative for corrections; SQLite remains a local cache.
 * A single atomic document contains the current snapshot, all earlier revisions and
 * their output jobs. The per-trade OS lock covers comparison and publication, so
 * interrupted local caching/export can be recovered from the shared document.
 */
public final class SharedTradeService {
    static final String DIRECTORY=".trade-revisions";
    private final TradeRepository repository;
    private final Path outputs, shared;
    private final String operator, workstation;
    private final java.util.function.Consumer<String> boundary;

    public SharedTradeService(TradeRepository repository, Path outputs, Path shared) {
        this(repository,outputs,shared,System.getProperty("user.name","Unknown account"),workstationName(),ignored -> {});
    }
    SharedTradeService(TradeRepository repository, Path outputs, Path shared, String operator,
                       String workstation, java.util.function.Consumer<String> boundary) {
        this.repository=repository;this.outputs=outputs;this.shared=shared.toAbsolutePath().normalize();
        this.operator=operator;this.workstation=workstation;this.boundary=boundary;
    }
    private static String workstationName() {
        String name=System.getenv("COMPUTERNAME");
        if (name==null || name.isBlank()) name=System.getenv("HOSTNAME");
        if (name==null || name.isBlank()) {
            try { name=java.net.InetAddress.getLocalHost().getHostName(); }
            catch (Exception ignored) { name="Unknown workstation"; }
        }
        return name;
    }

    public TradeDraft open(UUID id, long expectedRevision) throws Exception {
        return locked(id,() -> {
            JSONObject document=loadOrCreate(id);
            TradeDraft current=snapshot(last(document));
            if (current.revision()!=expectedRevision) throw changed();
            cache(document);
            repository.retryOutputs(outputs);
            return current;
        });
    }

    public int update(TradeDraft draft, long expectedRevision) throws Exception {
        draft.validateForApproval();
        if (draft.revision()<=expectedRevision) throw new IllegalArgumentException("A correction must have a newer revision");
        return locked(draft.id(),() -> {
            JSONObject document=loadOrCreate(draft.id());
            JSONObject latest=last(document);
            TradeDraft current=snapshot(latest);
            boolean retry=current.toJson().similar(draft.toJson()) && latest.optLong("parent",-1)==expectedRevision;
            if (!retry) {
                if (current.revision()!=expectedRevision) throw changed();
                JSONObject next=entry(draft,TradeApplicationService.outputs(draft,true),operator,workstation,Instant.now().toString());
                next.put("parent",expectedRevision);
                document.getJSONArray("versions").put(next);
                // This is the commit point. Never publish receipt files before it.
                boundary.accept("beforeSharedCommit");
                AtomicFiles.write(documentPath(shared,draft.id()),document.toString(2));
                boundary.accept("afterSharedCommit");
            }
            cache(document);
            int pending=repository.retryOutputs(outputs);
            pending+=publish(document,shared);
            return pending;
        });
    }

    private interface Operation<T> { T run() throws Exception; }
    private <T> T locked(UUID id, Operation<T> operation) throws Exception {
        if (!Files.isDirectory(shared)) throw new IOException("Shared Trades Folder is unavailable. Reconnect it before saving corrections.");
        String bound=repository.sharedSource(id);
        if (!bound.isBlank() && !Files.isSameFile(Path.of(bound),shared))
            throw new IOException("This trade belongs to a different Shared Trades Folder. Restore that folder in Preferences.");
        Path directory=shared.resolve(DIRECTORY);
        Files.createDirectories(directory);
        try (FileChannel channel=FileChannel.open(directory.resolve(id+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE)) {
            try (var lock=channel.tryLock()) {
                if (lock==null) throw busy();
                return operation.run();
            } catch (OverlappingFileLockException busy) { throw busy(); }
        }
    }
    private static IOException busy() {
        return new IOException("Another workstation is saving this trade. Wait for it to finish, then retry.");
    }
    private static IOException changed() {
        return new IOException("This trade changed on another workstation or in another window. Your draft is retained. Refresh History and reopen the latest revision before applying your correction.");
    }

    private JSONObject loadOrCreate(UUID id) throws Exception {
        Path file=documentPath(shared,id);
        TreeMap<Long,TradeDraft> available=new TreeMap<>();
        Map<Long,Path> sources=new HashMap<>();
        for (TradeDraft draft:repository.versions(id)) add(available,draft);
        // Compatibility with trades exported before shared editing was introduced.
        try (var files=Files.list(shared)) {
            for (Path candidate:files.filter(p -> p.getFileName().toString().endsWith(".json") && Files.isRegularFile(p)).toList()) {
                JSONObject json;
                try { json=new JSONObject(Files.readString(candidate)); }
                catch (RuntimeException invalid) { throw new IOException("Unreadable shared trade JSON: "+candidate.getFileName(),invalid); }
                if (!id.toString().equals(json.optString("id"))) continue;
                TradeDraft draft=TradeDraft.fromJson(json);draft.validateForApproval();
                add(available,draft);sources.put(draft.revision(),candidate);
            }
        }
        JSONObject document;
        if (Files.exists(file)) {
            document=readDocument(file,id);
            TreeMap<Long,TradeDraft> committed=new TreeMap<>();
            for (Object value:document.getJSONArray("versions")) add(committed,snapshot((JSONObject)value));
            for (var candidate:available.entrySet()) {
                var known=committed.get(candidate.getKey());
                if (known!=null) same(known,candidate.getValue());
                else if (candidate.getKey()>=committed.lastKey())
                    throw new IOException("A trade revision was saved outside shared editing. Review the conflicting files before editing; all workstations must use the updated app.");
            }
        } else {
            if (!repository.sharedSource(id).isBlank())
                throw new IOException("This trade's shared revision history is missing. Restore the shared history before editing; the local copy may be out of date.");
            if (available.isEmpty()) throw new IOException("The complete trade JSON could not be found in the Shared Trades Folder.");
            JSONArray versions=new JSONArray();
            for (TradeDraft draft:available.values()) {
                var jobs=TradeApplicationService.outputs(draft,draft.revision()!=available.firstKey());
                Path source=sources.get(draft.revision());
                if (source!=null) {
                    // Preserve the original names and bytes across time zones/app versions.
                    String stem=source.getFileName().toString().replaceFirst("\\.json$","");
                    List<TradeRepository.Output> originalJobs=new ArrayList<>();
                    for (var job:jobs) {
                        String extension=job.name().substring(job.name().lastIndexOf('.'));
                        Path companion=source.resolveSibling(stem+extension);
                        originalJobs.add(new TradeRepository.Output(stem+extension,
                                Files.exists(companion) ? Files.readString(companion) : job.content()));
                    }
                    jobs=originalJobs;
                }
                versions.put(entry(draft,jobs,"Not recorded (existing trade)","Not recorded",draft.quotedAt()));
            }
            document=new JSONObject().put("schema",1).put("id",id.toString()).put("versions",versions);
            AtomicFiles.write(file,document.toString(2));
        }
        repository.bindSharedSource(id,shared);
        return document;
    }

    private static void add(Map<Long,TradeDraft> versions, TradeDraft draft) throws IOException {
        TradeDraft previous=versions.putIfAbsent(draft.revision(),draft);
        if (previous!=null) same(previous,draft);
    }
    private static void same(TradeDraft a, TradeDraft b) throws IOException {
        if (!a.toJson().similar(b.toJson())) throw new IOException("Conflicting copies of revision "+a.revision()+" were found. The originals are retained; resolve the conflict before editing.");
    }
    private void cache(JSONObject document) throws Exception {
        for (Object value:document.getJSONArray("versions")) {
            JSONObject version=(JSONObject)value;
            TradeDraft draft=snapshot(version), local=repository.committedDraft(draft.id());
            if (local==null) repository.commit(draft,jobs(version));
            else if (local.revision()<draft.revision()) repository.revise(draft,local.revision(),jobs(version));
            else if (local.revision()==draft.revision()) same(local,draft);
        }
    }
    private static JSONObject entry(TradeDraft draft, List<TradeRepository.Output> jobs, String operator, String workstation, String at) {
        JSONArray outputs=new JSONArray();
        for (var job:jobs) outputs.put(new JSONObject().put("name",job.name()).put("content",job.content()));
        return new JSONObject().put("snapshot",new JSONObject(draft.toJson().toString())).put("outputs",outputs)
                .put("operator",operator).put("workstation",workstation).put("savedAt",at);
    }
    private static List<TradeRepository.Output> jobs(JSONObject entry) throws IOException {
        List<TradeRepository.Output> result=new ArrayList<>();
        Set<String> names=new HashSet<>();
        for (Object value:entry.getJSONArray("outputs")) {
            JSONObject job=(JSONObject)value;
            String name=job.getString("name");
            if (name.isBlank() || name.contains("/") || name.contains("\\") || name.contains(":")
                    || name.equals(".") || name.equals("..") || !names.add(name)) throw new IOException("Invalid shared trade output name");
            result.add(new TradeRepository.Output(name,job.getString("content")));
        }
        return result;
    }
    static Path documentPath(Path folder, UUID id) { return folder.resolve(DIRECTORY).resolve(id+".json"); }
    private static JSONObject last(JSONObject document) { return document.getJSONArray("versions").getJSONObject(document.getJSONArray("versions").length()-1); }
    private static TradeDraft snapshot(JSONObject entry) { return TradeDraft.fromJson(entry.getJSONObject("snapshot")); }
    private static JSONObject readDocument(Path file, UUID id) throws IOException {
        try {
            JSONObject doc=new JSONObject(Files.readString(file));
            if (doc.getInt("schema")!=1 || !id.toString().equals(doc.getString("id")) || doc.getJSONArray("versions").isEmpty())
                throw new IOException("Invalid shared trade revision history");
            long previous=-1;
            for (Object value:doc.getJSONArray("versions")) {
                JSONObject entry=(JSONObject)value;
                TradeDraft draft=snapshot(entry);draft.validateForApproval();
                if (!draft.id().equals(id) || draft.revision()<=previous) throw new IOException("Invalid shared trade revision order");
                previous=draft.revision();jobs(entry);
            }
            return doc;
        } catch (RuntimeException invalid) { throw new IOException("Could not read shared trade revision history: "+file.getFileName(),invalid); }
    }

    /** Idempotent, immutable exports. The shared document is also the durable retry queue. */
    private static int publish(JSONObject document, Path shared) throws IOException {
        int pending=0;
        for (Object value:document.getJSONArray("versions")) for (var job:jobs((JSONObject)value)) {
            Path destination=shared.resolve(job.name());
            try {
                if (!Files.isDirectory(shared)) throw new IOException("Shared folder unavailable");
                if (Files.exists(destination)) {
                    if (!Files.readString(destination).equals(job.content())) throw new IOException("Shared output has different contents");
                } else {
                    // A non-replacing move also protects against older background exporters.
                    Path staged=Files.createTempFile(shared,"trade-",".tmp");
                    try { Files.writeString(staged,job.content());Files.move(staged,destination); }
                    finally { Files.deleteIfExists(staged); }
                }
            } catch (IOException failure) { pending++; }
        }
        return pending;
    }
    public static int retrySharedOutputs(Path shared) throws IOException {
        int pending=0;
        for (JSONObject document:documents(shared)) pending+=publish(document,shared);
        return pending;
    }
    private static List<JSONObject> documents(Path shared) throws IOException {
        Path directory=shared.resolve(DIRECTORY);
        if (!Files.exists(directory)) return List.of();
        List<JSONObject> result=new ArrayList<>();
        try (var files=Files.list(directory)) {
            for (Path file:files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                String name=file.getFileName().toString();
                try { result.add(readDocument(file,UUID.fromString(name.substring(0,name.length()-5)))); }
                catch (IllegalArgumentException invalid) { throw new IOException("Invalid shared trade filename: "+name,invalid); }
            }
        }
        return result;
    }
    public static List<TradeRecord> history(Path shared) throws IOException {
        List<TradeRecord> records=new ArrayList<>();
        for (JSONObject document:documents(shared)) {
            JSONObject latest=last(document);
            TradeDraft draft=snapshot(latest);
            String filename=jobs(latest).stream().filter(job -> job.name().endsWith(".txt")).findFirst().orElseThrow().name();
            LocalDateTime date;
            try { date=LocalDateTime.ofInstant(Instant.parse(snapshot(document.getJSONArray("versions").getJSONObject(0)).quotedAt()),ZoneId.systemDefault()); }
            catch (java.time.format.DateTimeParseException unknown) { date=LocalDateTime.of(1970,1,1,0,0); }
            records.add(new TradeRecord(shared.resolve(filename).toString(),date,draft.customer(),draft.trader(),draft.payment(),
                    draft.settle().market(),draft.lines().stream().mapToInt(line -> line.quantity()).sum(),draft.id(),draft.revision(),false));
        }
        return records;
    }
    public static String receipt(Path shared, UUID id, long revision) throws IOException {
        Path file=documentPath(shared,id);
        if (!Files.exists(file)) return null;
        for (Object value:readDocument(file,id).getJSONArray("versions")) {
            JSONObject version=(JSONObject)value;
            if (snapshot(version).revision()==revision)
                return jobs(version).stream().filter(job -> job.name().endsWith(".txt")).findFirst().orElseThrow().content();
        }
        return null;
    }

    public static String revisionHistory(TradeRepository repository, Path shared, UUID id) throws Exception {
        JSONArray versions;
        Path file=shared==null ? null : documentPath(shared,id);
        if (file!=null && Files.exists(file)) versions=readDocument(file,id).getJSONArray("versions");
        else {
            versions=new JSONArray();
            for (TradeDraft draft:repository.versions(id)) versions.put(entry(draft,List.of(),"Not recorded (local trade)","Not recorded",draft.quotedAt()));
        }
        if (versions.isEmpty()) return "No structured revision history is available for this trade.";
        StringBuilder text=new StringBuilder();
        TradeDraft previous=null;
        for (Object value:versions) {
            JSONObject version=(JSONObject)value;
            TradeDraft draft=snapshot(version);
            text.append("Revision ").append(draft.revision()).append(" | ").append(version.optString("savedAt")).append('\n')
                    .append("Account: ").append(version.optString("operator")).append(" | Workstation: ").append(version.optString("workstation")).append('\n')
                    .append(changes(previous,draft)).append("\n\n");
            previous=draft;
        }
        return text.toString();
    }
    private static String changes(TradeDraft before, TradeDraft after) {
        if (before==null) return "Original saved trade";
        List<String> changes=new ArrayList<>();
        JSONObject a=before.toJson(),b=after.toJson();
        String[][] fields={{"customer","Customer"},{"trader","Employee"},{"identification","Identification"},
                {"checkNumber","Check number"},{"payment","Payment method"},{"credit","Credit amount"},{"check","Check amount"}};
        for (String[] field:fields) if (!Objects.equals(a.get(field[0]),b.get(field[0]))) {
            changes.add(field[0].equals("identification") ? "Identification changed" : field[1]+": "+a.get(field[0])+" → "+b.get(field[0]));
        }
        Map<UUID,com.cardpricer.model.TradeLine> oldLines=new LinkedHashMap<>();
        for (var line:before.lines()) oldLines.put(line.id(),line);
        for (var line:after.lines()) {
            var old=oldLines.remove(line.id());
            String name=line.card().getName();
            if (old==null) changes.add("Added: "+name+" × "+line.quantity());
            else if (!old.toJson().similar(line.toJson())) {
                List<String> details=new ArrayList<>();
                String[][] lineFields={{"quantity","quantity"},{"condition","condition"},{"finish","finish"},
                        {"base","base price"},{"valuation","price"},{"creditRate","credit rate"},{"checkRate","check rate"},
                        {"overrideCondition","override condition"},{"overrideValue","override price"}};
                JSONObject oldJson=old.toJson(),newJson=line.toJson();
                for (String[] field:lineFields) if (!Objects.equals(oldJson.opt(field[0]),newJson.opt(field[0])))
                    details.add(field[1]+" "+oldJson.opt(field[0])+" → "+newJson.opt(field[0]));
                if (!oldJson.getJSONObject("card").similar(newJson.getJSONObject("card"))) details.add("card details updated");
                changes.add("Changed: "+name+" ("+String.join(", ",details)+")");
            }
        }
        for (var line:oldLines.values()) changes.add("Removed: "+line.card().getName()+" × "+line.quantity());
        return changes.isEmpty() ? "Saved again; no customer, payment or card changes" : String.join("\n",changes);
    }
}
