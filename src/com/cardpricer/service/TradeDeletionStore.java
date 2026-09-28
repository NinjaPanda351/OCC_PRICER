package com.cardpricer.service;

import com.cardpricer.model.TradeRecord;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** Durable deletion intent, independent of whether file cleanup or network sync has finished. */
final class TradeDeletionStore {
    record Deletion(String key, Set<String> files) {
        Deletion {
            if(key.startsWith("trade:")) UUID.fromString(key.substring(6));
            else if(key.startsWith("receipt:")) validateName(key.substring(8));
            else throw new IllegalArgumentException("Invalid deleted trade identity");
            files.forEach(TradeDeletionStore::validateName);
            if(files.isEmpty()) throw new IllegalArgumentException("No trade files specified");
            files=Collections.unmodifiableSet(new TreeSet<>(files));
        }
        JSONObject json() { return new JSONObject().put("schema",1).put("key",key).put("files",new JSONArray(files)); }
        static Deletion parse(String text) {
            var json=new JSONObject(text);
            if(json.getInt("schema")!=1) throw new IllegalArgumentException("Unsupported deletion record");
            var names=new TreeSet<String>();for(Object value:json.getJSONArray("files")) names.add((String)value);
            return new Deletion(json.getString("key"),names);
        }
        UUID tradeId() { return key.startsWith("trade:") ? UUID.fromString(key.substring(6)) : null; }
        Deletion merge(Deletion other) {
            if(!key.equals(other.key)) throw new IllegalArgumentException("Different deletion identities");
            var names=new TreeSet<>(files);names.addAll(other.files);return new Deletion(key,names);
        }
    }
    record Saved(Deletion deletion,String sharedFolder) {}
    record Snapshot(Set<String> keys,Set<String> files) {
        boolean contains(TradeRecord record) { return keys.contains(record.historyKey()) || containsFile(Path.of(record.filename).getFileName().toString()); }
        boolean containsFile(String name) { return files.contains(name.toLowerCase(Locale.ROOT)); }
    }
    private final TradeRepository repository;
    TradeDeletionStore(TradeRepository repository) { this.repository=repository; }

    static void validateName(String name) {
        if(name.isBlank() || name.contains("/") || name.contains("\\") || name.contains(":") || name.contains("\0")
                || !name.matches("(?is).+\\.(csv|txt|json|pdf)")) throw new IllegalArgumentException("Invalid deleted trade filename");
    }
    static void create(Connection c) throws SQLException {
        try(var s=c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS trade_deletions(record_key TEXT PRIMARY KEY,document TEXT NOT NULL,shared_folder TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS deleted_trade_files(name TEXT PRIMARY KEY COLLATE NOCASE,record_key TEXT NOT NULL)");
        }
    }
    static boolean contains(Connection c,String key,String name) throws SQLException {
        try(var s=c.prepareStatement("SELECT EXISTS(SELECT 1 FROM trade_deletions WHERE record_key=?) OR EXISTS(SELECT 1 FROM deleted_trade_files WHERE name=?)")) {
            s.setString(1,key);s.setString(2,name);try(var rows=s.executeQuery()) { return rows.getBoolean(1); }
        }
    }
    boolean contains(String key,String name) throws SQLException { try(var c=repository.connect()) { return contains(c,key,name); } }
    Snapshot snapshot() throws SQLException {
        var keys=new HashSet<String>();var files=new HashSet<String>();
        try(var c=repository.connect();var s=c.createStatement()) {
            try(var rows=s.executeQuery("SELECT record_key FROM trade_deletions")) { while(rows.next()) keys.add(rows.getString(1)); }
            try(var rows=s.executeQuery("SELECT name FROM deleted_trade_files")) { while(rows.next()) files.add(rows.getString(1).toLowerCase(Locale.ROOT)); }
        }
        return new Snapshot(keys,files);
    }
    List<Saved> all() throws SQLException {
        var result=new ArrayList<Saved>();
        try(var c=repository.connect();var s=c.createStatement();var rows=s.executeQuery("SELECT document,shared_folder FROM trade_deletions")) {
            while(rows.next()) result.add(new Saved(Deletion.parse(rows.getString(1)),rows.getString(2)));
        }
        return result;
    }
    Deletion record(Deletion deletion,String sharedFolder) throws SQLException {
        synchronized(TradeDeletionService.LOCAL_LOCK) {
            try(var c=repository.connect()) {
                c.setAutoCommit(false);
                try {
                    try(var s=c.prepareStatement("SELECT document,shared_folder FROM trade_deletions WHERE record_key=?")) {
                        s.setString(1,deletion.key());try(var rows=s.executeQuery()) {
                            if(rows.next()) {
                                deletion=deletion.merge(Deletion.parse(rows.getString(1)));
                                if(sharedFolder.isBlank()) sharedFolder=rows.getString(2);
                            }
                        }
                    }
                    var names=new TreeSet<>(deletion.files());
                    if(deletion.tradeId()!=null) try(var s=c.prepareStatement("SELECT name FROM jobs WHERE trade_id=?")) {
                        s.setString(1,deletion.tradeId().toString());try(var rows=s.executeQuery()) {
                            while(rows.next()) TradeDeletionService.addCompanions(names,rows.getString(1));
                        }
                    }
                    deletion=new Deletion(deletion.key(),names);
                    try(var s=c.prepareStatement("INSERT INTO trade_deletions VALUES(?,?,?) ON CONFLICT(record_key) DO UPDATE SET document=excluded.document,shared_folder=excluded.shared_folder")) {
                        s.setString(1,deletion.key());s.setString(2,deletion.json().toString());s.setString(3,sharedFolder);s.executeUpdate();
                    }
                    try(var s=c.prepareStatement("INSERT OR IGNORE INTO deleted_trade_files VALUES(?,?)")) {
                        for(String name:deletion.files()) { s.setString(1,name);s.setString(2,deletion.key());s.addBatch(); }s.executeBatch();
                    }
                    try(var s=c.prepareStatement("UPDATE jobs SET status='deleted',error=NULL WHERE trade_id=? OR name IN (SELECT name FROM deleted_trade_files)")) {
                        s.setString(1,deletion.tradeId()==null ? "" : deletion.tradeId().toString());s.executeUpdate();
                    }
                    c.commit();return deletion;
                } catch(Exception failure) { c.rollback();throw failure; }
            }
        }
    }
}
