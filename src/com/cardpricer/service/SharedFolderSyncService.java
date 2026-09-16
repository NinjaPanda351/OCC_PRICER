package com.cardpricer.service;
import com.cardpricer.util.AtomicFiles;
import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Persistent output-copy queue. Hash mismatch is a conflict, never an overwrite. */
public final class SharedFolderSyncService {
    private final Path database;
    public SharedFolderSyncService(Path database) throws Exception {
        this.database=database.toAbsolutePath(); Files.createDirectories(this.database.getParent());
        try (Connection c=connect(); Statement s=c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS sync_jobs(source TEXT NOT NULL,destination TEXT NOT NULL,hash TEXT NOT NULL,"
                    + "status TEXT NOT NULL DEFAULT 'pending',attempts INTEGER NOT NULL DEFAULT 0,next_attempt INTEGER NOT NULL DEFAULT 0,error TEXT,PRIMARY KEY(source,destination))");
        }
    }
    private Connection connect() throws SQLException {
        Connection c=DriverManager.getConnection("jdbc:sqlite:"+database);
        try (Statement s=c.createStatement()) { s.execute("PRAGMA busy_timeout=5000"); }
        return c;
    }
    public void enqueue(Path source,Path destination) throws Exception {
        String hash=AtomicFiles.hash(Files.readAllBytes(source));
        try (Connection c=connect(); PreparedStatement s=c.prepareStatement("INSERT INTO sync_jobs(source,destination,hash) VALUES(?,?,?) "
                + "ON CONFLICT(source,destination) DO UPDATE SET status='conflict',error='Local output changed after queuing' WHERE hash!=excluded.hash")) {
            s.setString(1,source.toAbsolutePath().toString());s.setString(2,destination.toAbsolutePath().toString());s.setString(3,hash);s.executeUpdate();
        }
    }
    private record Job(String source,String destination,String hash,int attempts) {}
    public int retry(boolean force) throws Exception {
        List<Job> jobs=new ArrayList<>();
        try (Connection c=connect(); PreparedStatement s=c.prepareStatement("SELECT * FROM sync_jobs WHERE (status='pending' OR (? AND status='conflict')) AND (? OR next_attempt<=?)")) {
            s.setBoolean(1,force);s.setBoolean(2,force);s.setLong(3,Instant.now().getEpochSecond());
            try (ResultSet rs=s.executeQuery()) { while(rs.next()) jobs.add(new Job(rs.getString("source"),rs.getString("destination"),rs.getString("hash"),rs.getInt("attempts"))); }
        }
        for (Job job:jobs) {
            String status="synced",error=null;
            try {
                Path source=Path.of(job.source),destination=Path.of(job.destination);
                if (!Files.isDirectory(destination.getParent())) throw new IOException("Shared folder unavailable");
                byte[] bytes=Files.readAllBytes(source);
                if (!job.hash.equals(AtomicFiles.hash(bytes))) { status="conflict";throw new IOException("Local content changed"); }
                if (Files.exists(destination)) {
                    if (!job.hash.equals(AtomicFiles.hash(Files.readAllBytes(destination)))) { status="conflict";throw new IOException("Shared content differs"); }
                } else {
                    Path staged=Files.createTempFile(destination.getParent(),"sync-",".tmp");
                    try { Files.write(staged,bytes); Files.move(staged,destination); }
                    finally { Files.deleteIfExists(staged); }
                }
            } catch (IOException failure) { if (!status.equals("conflict")) status="pending"; error=failure.getMessage(); }
            try (Connection c=connect(); PreparedStatement s=c.prepareStatement("UPDATE sync_jobs SET status=?,attempts=attempts+1,next_attempt=?,error=? WHERE source=? AND destination=?")) {
                s.setString(1,status);s.setLong(2,Instant.now().getEpochSecond()+Math.min(3600,30L << Math.min(7,job.attempts)));
                s.setString(3,error);s.setString(4,job.source);s.setString(5,job.destination);s.executeUpdate();
            }
        }
        try (Connection c=connect(); Statement s=c.createStatement();ResultSet rs=s.executeQuery("SELECT COUNT(*) FROM sync_jobs WHERE status!='synced'")) { return rs.getInt(1); }
    }
}
