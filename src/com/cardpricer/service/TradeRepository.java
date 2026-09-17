package com.cardpricer.service;

import com.cardpricer.model.TradeDraft;
import com.cardpricer.model.InventoryStatusChange;
import com.cardpricer.util.AtomicFiles;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Workstation-only SQLite ledger. Snapshot and output jobs commit in one transaction. */
public final class TradeRepository {
    public record Output(String name, String content) {}
    public record Job(long id, String tradeId, String name, String content, String hash, int attempts) {}
    private final Path database;
    private final java.util.function.Consumer<String> boundary;
    public TradeRepository(Path database) throws SQLException, IOException {
        this(database, ignored -> {});
    }
    TradeRepository(Path database, java.util.function.Consumer<String> boundary) throws SQLException, IOException {
        this.database = database.toAbsolutePath();
        this.boundary = boundary;
        Files.createDirectories(this.database.getParent());
        try (Connection connection = connect(); Statement s = connection.createStatement()) {
            int version;
            try (ResultSet rs = s.executeQuery("PRAGMA user_version")) { version = rs.getInt(1); }
            if (version > 3) throw new SQLException("Ledger was created by a newer application");
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("CREATE TABLE IF NOT EXISTS drafts(id TEXT PRIMARY KEY, revision INTEGER NOT NULL, snapshot TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS trades(id TEXT PRIMARY KEY, approved_at TEXT NOT NULL, snapshot TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS settlements(trade_id TEXT PRIMARY KEY REFERENCES trades(id), snapshot TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS legacy_records(id TEXT PRIMARY KEY, filename TEXT NOT NULL, attachment BLOB NOT NULL, imported_at TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS jobs(id INTEGER PRIMARY KEY, trade_id TEXT NOT NULL REFERENCES trades(id), "
                    + "name TEXT NOT NULL, content TEXT NOT NULL, hash TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'pending', "
                    + "attempts INTEGER NOT NULL DEFAULT 0, error TEXT, UNIQUE(trade_id,name))");
            s.execute("CREATE TABLE IF NOT EXISTS trade_versions(trade_id TEXT NOT NULL, revision INTEGER NOT NULL, snapshot TEXT NOT NULL, archived_at TEXT NOT NULL, PRIMARY KEY(trade_id,revision))");
            s.execute("CREATE TABLE IF NOT EXISTS shared_trade_sources(trade_id TEXT PRIMARY KEY, folder TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS inventory_status(record_key TEXT PRIMARY KEY, revision INTEGER NOT NULL, inventoried INTEGER NOT NULL, updated_at TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS inventory_changes(id TEXT PRIMARY KEY, record_key TEXT NOT NULL, revision INTEGER NOT NULL, sequence INTEGER NOT NULL, inventoried INTEGER NOT NULL, updated_at TEXT NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS inventory_changes_record ON inventory_changes(record_key,revision,sequence)");
            if (version < 3) {
                connection.setAutoCommit(false);
                try {
                    try (ResultSet rs=s.executeQuery("SELECT * FROM inventory_status")) {
                        while (rs.next()) {
                            String key=rs.getString("record_key"), at=rs.getString("updated_at");
                            long revision=rs.getLong("revision");boolean value=rs.getBoolean("inventoried");
                            UUID id=UUID.nameUUIDFromBytes((key+"\n"+revision+"\n"+value+"\n"+at).getBytes(StandardCharsets.UTF_8));
                            insertInventoryChange(connection,new InventoryStatusChange(id,key,revision,1,value,Instant.parse(at)));
                        }
                    }
                    s.execute("PRAGMA user_version=3");
                    connection.commit();
                } catch (Exception failure) { connection.rollback();throw failure; }
            }
        }
    }
    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA busy_timeout=5000"); s.execute("PRAGMA foreign_keys=ON"); s.execute("PRAGMA synchronous=FULL");
        }
        return connection;
    }
    public void saveDraft(TradeDraft draft) throws SQLException {
        try (Connection c = connect(); PreparedStatement s = c.prepareStatement("INSERT INTO drafts SELECT ?,?,? WHERE NOT EXISTS (SELECT 1 FROM trades WHERE id=?) "
                + "ON CONFLICT(id) DO UPDATE SET revision=excluded.revision,snapshot=excluded.snapshot WHERE excluded.revision>=drafts.revision")) {
            s.setString(1, draft.id().toString()); s.setLong(2, draft.revision()); s.setString(3, draft.toJson().toString());
            s.setString(4, draft.id().toString()); s.executeUpdate();
        }
    }
    public void deleteDraft(UUID id) throws SQLException {
        try (Connection c=connect();PreparedStatement s=c.prepareStatement("DELETE FROM drafts WHERE id=?")) {
            s.setString(1,id.toString());s.executeUpdate();
        }
    }
    public boolean isCommitted(UUID id) throws SQLException {
        try (Connection c = connect(); PreparedStatement s = c.prepareStatement("SELECT 1 FROM trades WHERE id=?")) {
            s.setString(1, id.toString()); try (ResultSet rs = s.executeQuery()) { return rs.next(); }
        }
    }
    public TradeDraft committedDraft(UUID id) throws SQLException {
        try (Connection c=connect(); PreparedStatement s=c.prepareStatement("SELECT snapshot FROM trades WHERE id=?")) {
            s.setString(1,id.toString());
            try (ResultSet rs=s.executeQuery()) { return rs.next() ? TradeDraft.fromJson(new JSONObject(rs.getString(1))) : null; }
        }
    }
    public String sharedSource(UUID id) throws SQLException {
        try (Connection c=connect(); PreparedStatement s=c.prepareStatement("SELECT folder FROM shared_trade_sources WHERE trade_id=?")) {
            s.setString(1,id.toString());
            try (ResultSet rs=s.executeQuery()) { return rs.next() ? rs.getString(1) : ""; }
        }
    }
    public void bindSharedSource(UUID id, Path folder) throws SQLException {
        try (Connection c=connect(); PreparedStatement s=c.prepareStatement("INSERT INTO shared_trade_sources VALUES(?,?) ON CONFLICT(trade_id) DO UPDATE SET folder=excluded.folder")) {
            s.setString(1,id.toString());s.setString(2,folder.toAbsolutePath().normalize().toString());s.executeUpdate();
        }
    }
    public List<TradeDraft> versions(UUID id) throws SQLException {
        List<TradeDraft> result=new ArrayList<>();
        try (Connection c=connect(); PreparedStatement s=c.prepareStatement("SELECT snapshot FROM trade_versions WHERE trade_id=? ORDER BY revision")) {
            s.setString(1,id.toString());
            try (ResultSet rs=s.executeQuery()) { while (rs.next()) result.add(TradeDraft.fromJson(new JSONObject(rs.getString(1)))); }
        }
        var current=committedDraft(id);
        if (current!=null) result.add(current);
        return result;
    }
    public void importLegacy(Path file) throws SQLException, IOException {
        byte[] attachment=Files.readAllBytes(file);
        try (Connection c=connect(); PreparedStatement s=c.prepareStatement("INSERT OR IGNORE INTO legacy_records VALUES(?,?,?,?)")) {
            s.setString(1,AtomicFiles.hash(attachment));s.setString(2,file.getFileName().toString());s.setBytes(3,attachment);
            s.setString(4,Instant.now().toString());s.executeUpdate();
        }
    }
    public List<com.cardpricer.model.TradeRecord> history(Path outputDirectory) throws SQLException {
        var records=new ArrayList<com.cardpricer.model.TradeRecord>();
        try (Connection c=connect(); Statement s=c.createStatement(); ResultSet rs=s.executeQuery("SELECT id,approved_at,snapshot, (SELECT name FROM jobs WHERE trade_id=trades.id AND name LIKE '%.txt' ORDER BY id DESC LIMIT 1) AS receipt FROM trades ORDER BY approved_at DESC")) {
            while(rs.next()) {
                TradeDraft draft=TradeDraft.fromJson(new JSONObject(rs.getString("snapshot")));
                String receipt=rs.getString("receipt");
                records.add(new com.cardpricer.model.TradeRecord(outputDirectory.resolve(receipt == null ? "trade_"+draft.id()+".txt" : receipt).toString(),
                        java.time.LocalDateTime.ofInstant(Instant.parse(rs.getString("approved_at")),java.time.ZoneId.systemDefault()),
                        draft.customer(),draft.trader(),draft.payment(),draft.settle().market(),draft.lines().stream().mapToInt(line->line.quantity()).sum(),draft.id(),draft.revision(),false));
            }
        }
        return records;
    }
    public void commit(TradeDraft draft, List<Output> outputs) throws SQLException {
        save(draft, outputs, null);
    }

    public void revise(TradeDraft draft, long expectedRevision, List<Output> outputs) throws SQLException {
        if (draft.revision() <= expectedRevision) throw new IllegalArgumentException("A correction must have a newer revision");
        save(draft, outputs, expectedRevision);
    }

    private void save(TradeDraft draft, List<Output> outputs, Long expectedRevision) throws SQLException {
        draft.validateForApproval(); // Validate before writing anything.
        String snapshot = draft.toJson().toString();
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement s = c.prepareStatement("SELECT snapshot FROM trades WHERE id=?")) {
                    s.setString(1, draft.id().toString());
                    try (ResultSet rs = s.executeQuery()) {
                        if (rs.next()) {
                            String previous=rs.getString(1);
                            if (new JSONObject(previous).similar(new JSONObject(snapshot))) { c.rollback(); return; }
                            if (expectedRevision == null)
                                throw new SQLException("This trade is already approved; retry its pending outputs");
                            TradeDraft old=TradeDraft.fromJson(new JSONObject(previous));
                            if (old.revision()!=expectedRevision) throw new SQLException("This trade changed since it was opened. Reopen it from History before editing.");
                            try (PreparedStatement archive=c.prepareStatement("INSERT INTO trade_versions VALUES(?,?,?,?)")) {
                                archive.setString(1,draft.id().toString());archive.setLong(2,old.revision());
                                archive.setString(3,previous);archive.setString(4,Instant.now().toString());archive.executeUpdate();
                            }
                        } else if (expectedRevision != null) {
                            throw new SQLException("The original trade could not be found");
                        }
                    }
                }
                try (PreparedStatement s = c.prepareStatement("INSERT INTO trades VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET snapshot=excluded.snapshot")) {
                    s.setString(1, draft.id().toString()); s.setString(2, Instant.now().toString()); s.setString(3, snapshot); s.executeUpdate();
                }
                var settled=draft.settle();
                var allocations=new org.json.JSONArray();
                for (var allocation:settled.lines()) allocations.put(new JSONObject().put("lineId",allocation.line().id())
                        .put("credit",allocation.credit().toPlainString()).put("check",allocation.check().toPlainString())
                        .put("cost",allocation.cost().toPlainString()).put("stock",allocation.line().stock()));
                var settlementJson=new JSONObject().put("schema",1).put("applicationVersion",com.cardpricer.util.AppVersion.CURRENT)
                        .put("credit",settled.credit().toPlainString()).put("check",settled.check().toPlainString()).put("allocations",allocations);
                try (PreparedStatement s=c.prepareStatement("INSERT INTO settlements VALUES(?,?) ON CONFLICT(trade_id) DO UPDATE SET snapshot=excluded.snapshot")) {
                    s.setString(1,draft.id().toString());s.setString(2,settlementJson.toString());s.executeUpdate();
                }
                for (Output output : outputs) {
                    try (PreparedStatement s = c.prepareStatement("INSERT INTO jobs(trade_id,name,content,hash) VALUES(?,?,?,?)")) {
                        s.setString(1, draft.id().toString()); s.setString(2, output.name()); s.setString(3, output.content());
                        s.setString(4, AtomicFiles.hash(output.content().getBytes(StandardCharsets.UTF_8))); s.executeUpdate();
                    }
                }
                try (PreparedStatement s = c.prepareStatement("DELETE FROM drafts WHERE id=?")) {
                    s.setString(1, draft.id().toString()); s.executeUpdate();
                }
                boundary.accept("beforeCommit");
                c.commit();
                boundary.accept("afterCommit");
            } catch (Exception failure) { c.rollback(); throw failure; }
        }
    }
    /** Status belongs to a particular revision: a correction needs POS review again. */
    public void setInventoried(com.cardpricer.model.TradeRecord record, boolean value) throws SQLException {
        // The SELECT and INSERT are one SQLite statement, so simultaneous local edits serialize.
        try (Connection c=connect(); PreparedStatement s=c.prepareStatement("INSERT INTO inventory_changes "
                + "SELECT ?,?,?,COALESCE(MAX(sequence),0)+1,?,? FROM inventory_changes WHERE record_key=? AND revision=?")) {
            s.setString(1,UUID.randomUUID().toString());s.setString(2,record.historyKey());s.setLong(3,record.revision);
            s.setBoolean(4,value);s.setString(5,Instant.now().toString());s.setString(6,record.historyKey());
            s.setLong(7,record.revision);s.executeUpdate();
        }
    }

    public List<com.cardpricer.model.TradeRecord> inventoryStatuses(List<com.cardpricer.model.TradeRecord> records) throws SQLException {
        Map<String,InventoryStatusChange> statuses=new HashMap<>();
        for (var change:inventoryChanges()) statuses.merge(change.recordKey(),change,
                (a,b) -> InventoryStatusChange.ORDER.compare(a,b)>=0 ? a : b);
        return records.stream().map(record -> {
            var status=statuses.get(record.historyKey());
            return record.withInventoried(status!=null && status.revision()==record.revision && status.inventoried());
        }).toList();
    }

    public List<InventoryStatusChange> inventoryChanges() throws SQLException {
        List<InventoryStatusChange> changes=new ArrayList<>();
        try (Connection c=connect(); Statement s=c.createStatement();ResultSet rs=s.executeQuery("SELECT * FROM inventory_changes")) {
            while(rs.next()) changes.add(new InventoryStatusChange(UUID.fromString(rs.getString("id")),rs.getString("record_key"),
                    rs.getLong("revision"),rs.getLong("sequence"),rs.getBoolean("inventoried"),Instant.parse(rs.getString("updated_at"))));
        }
        return changes;
    }

    public boolean importInventoryChange(InventoryStatusChange change) throws SQLException {
        try (Connection c=connect()) { return insertInventoryChange(c,change); }
    }

    private static boolean insertInventoryChange(Connection c,InventoryStatusChange change) throws SQLException {
        try (PreparedStatement s=c.prepareStatement("INSERT OR IGNORE INTO inventory_changes VALUES(?,?,?,?,?,?)")) {
            s.setString(1,change.id().toString());s.setString(2,change.recordKey());s.setLong(3,change.revision());
            s.setLong(4,change.sequence());s.setBoolean(5,change.inventoried());s.setString(6,change.updatedAt().toString());
            return s.executeUpdate()!=0;
        }
    }

    public String receiptContent(UUID tradeId) throws SQLException {
        try (Connection c=connect(); PreparedStatement s=c.prepareStatement("SELECT content FROM jobs WHERE trade_id=? AND name LIKE '%.txt' ORDER BY id DESC LIMIT 1")) {
            s.setString(1,tradeId.toString());
            try (ResultSet rs=s.executeQuery()) { return rs.next() ? rs.getString(1) : ""; }
        }
    }
    public List<Job> pending() throws SQLException {
        List<Job> jobs = new ArrayList<>();
        try (Connection c = connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT * FROM jobs WHERE status!='done' ORDER BY id")) {
            while (rs.next()) jobs.add(new Job(rs.getLong("id"), rs.getString("trade_id"), rs.getString("name"),
                    rs.getString("content"), rs.getString("hash"), rs.getInt("attempts")));
        }
        return jobs;
    }
    public void recordAttempt(long id, String error) throws SQLException {
        try (Connection c = connect(); PreparedStatement s = c.prepareStatement("UPDATE jobs SET status=?,attempts=attempts+1,error=? WHERE id=?")) {
            s.setString(1, error == null ? "done" : "pending"); s.setString(2, error); s.setLong(3, id); s.executeUpdate();
        }
    }
    public int retryOutputs(Path destination) throws SQLException {
        int failed = 0;
        for (Job job : pending()) {
            try {
                Path root = destination.toAbsolutePath().normalize();
                Path file = root.resolve(job.name()).normalize();
                if (!file.getParent().equals(root)) throw new IOException("Invalid output name");
                if (Files.exists(file)) {
                    if (!AtomicFiles.hash(Files.readAllBytes(file)).equals(job.hash())) throw new IOException("Existing output has different contents");
                } else AtomicFiles.write(file, job.content());
                boundary.accept("afterOutputWrite");
                recordAttempt(job.id(), null);
            } catch (IOException e) { recordAttempt(job.id(), e.getMessage()); failed++; }
        }
        return failed;
    }
}
