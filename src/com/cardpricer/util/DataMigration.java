package com.cardpricer.util;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.file.*;
/** Resumable verified copies preserve originals and never replace different destination files. */
public final class DataMigration {
    private DataMigration() {}
    public static void migrate(Path source, Path destination) throws IOException {
        source=source.toAbsolutePath().normalize(); destination=destination.toAbsolutePath().normalize();
        if (!Files.isDirectory(source) || source.equals(destination)) return;
        Files.createDirectories(destination);
        Path manifest=destination.resolve("migration-v1.json");
        JSONObject completed=Files.exists(manifest) ? new JSONObject(Files.readString(manifest)) : new JSONObject();
        try (var paths=Files.walk(source)) {
            for (Path file:paths.filter(Files::isRegularFile).toList()) {
                if (Files.isSymbolicLink(file)) continue;
                Path relative=source.relativize(file); Path target=destination.resolve(relative).normalize();
                if (!target.startsWith(destination)) throw new IOException("Migration path escapes destination");
                String hash=AtomicFiles.hash(Files.readAllBytes(file));
                if (Files.exists(target)) {
                    if (!hash.equals(AtomicFiles.hash(Files.readAllBytes(target)))) throw new IOException("Migration conflict: "+relative);
                } else {
                    Files.createDirectories(target.getParent());
                    Path temp=Files.createTempFile(target.getParent(),"migration-",".tmp");
                    try {
                        Files.copy(file,temp,StandardCopyOption.REPLACE_EXISTING);
                        if (!hash.equals(AtomicFiles.hash(Files.readAllBytes(temp)))) throw new IOException("Migration verification failed");
                        AtomicFiles.replace(temp,target);
                    } finally { Files.deleteIfExists(temp); }
                }
                completed.put(relative.toString(),hash); AtomicFiles.write(manifest,completed.toString(2));
            }
        }
    }
}
