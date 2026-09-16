package com.cardpricer.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class AtomicFiles {
    private AtomicFiles() {}
    public static void write(Path path, String text) throws IOException {
        path = path.toAbsolutePath();
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), ".pending-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(text);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            replace(temp, path);
        } finally { Files.deleteIfExists(temp); }
    }
    public static void replace(Path source, Path destination) throws IOException {
        // Fail closed when the filesystem cannot preserve the previous usable file atomically.
        Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
