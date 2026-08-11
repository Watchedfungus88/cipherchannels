package dev.cipherchannels.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.cipherchannels.channels.ReplayRecord;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ReplayStore implements AutoCloseable {
    private static final String FILE_NAME = "cipherchannels-replay.json";
    private static final long MAX_FILE_BYTES = 1_048_576;
    private final Path file;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CipherChannels replay storage");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<List<ReplayRecord>> pending = new AtomicReference<>();
    private final AtomicBoolean draining = new AtomicBoolean();
    private volatile boolean healthy = true;
    private volatile String notice = "";

    public ReplayStore(Path configDirectory) {
        file = configDirectory.resolve(FILE_NAME);
    }

    public LoadedReplay load() {
        if (!Files.isRegularFile(file)) {
            Path backup = backupFile();
            if (!Files.isRegularFile(backup)) {
                return new LoadedReplay(List.of(), true, "");
            }
            List<ReplayRecord> records;
            try {
                records = read(backup);
            } catch (IOException | RuntimeException exception) {
                preserve(backup);
                healthy = false;
                notice = "cipherchannels.notice.replay.reset";
                return new LoadedReplay(List.of(), false, notice);
            }
            return restore(records, "cipherchannels.notice.replay.recovered_backup");
        }
        try {
            return new LoadedReplay(read(file), true, "");
        } catch (IOException | RuntimeException exception) {
            preserve(file);
            List<ReplayRecord> records;
            try {
                records = read(backupFile());
            } catch (IOException | RuntimeException backupFailure) {
                preserve(backupFile());
                healthy = false;
                notice = "cipherchannels.notice.replay.reset";
                return new LoadedReplay(List.of(), false, notice);
            }
            return restore(records, "cipherchannels.notice.replay.recovered_primary");
        }
    }

    public void save(List<ReplayRecord> records) {
        pending.set(List.copyOf(records));
        schedule();
    }

    public boolean healthy() {
        return healthy;
    }

    public String takeNotice() {
        String result = notice;
        notice = "";
        return result;
    }

    @Override
    public void close() {
        schedule();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                healthy = false;
                writer.shutdownNow();
                writer.awaitTermination(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            healthy = false;
            writer.shutdownNow();
        }
        List<ReplayRecord> remaining = writer.isTerminated() ? pending.getAndSet(null) : null;
        if (remaining != null) {
            try {
                write(remaining);
            } catch (RuntimeException exception) {
                healthy = false;
                notice = "cipherchannels.notice.replay.write_failed";
            }
        }
    }

    private void schedule() {
        if (!writer.isShutdown() && pending.get() != null && draining.compareAndSet(false, true)) {
            writer.execute(this::drain);
        }
    }

    private void drain() {
        try {
            List<ReplayRecord> records;
            while ((records = pending.getAndSet(null)) != null) {
                write(records);
            }
        } catch (RuntimeException exception) {
            healthy = false;
            notice = "cipherchannels.notice.replay.write_failed";
        } finally {
            draining.set(false);
            schedule();
        }
    }

    private List<ReplayRecord> read(Path source) throws IOException {
        if (Files.size(source) > MAX_FILE_BYTES) throw new IOException("Replay file is too large");
        JsonElement parsed = JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Replay root is not an object");
        }
        JsonObject root = parsed.getAsJsonObject();
        if (root.get("version").getAsInt() != 1 || !root.get("entries").isJsonArray()) {
            throw new IllegalArgumentException("Unsupported replay format");
        }
        List<ReplayRecord> records = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("entries")) {
            if (records.size() == 4_096) throw new IllegalArgumentException("Too many replay entries");
            JsonObject entry = element.getAsJsonObject();
            String fingerprint = entry.get("fingerprint").getAsString();
            String digest = entry.get("digest").getAsString();
            byte[] decoded = Base64.getUrlDecoder().decode(digest);
            if (!fingerprint.matches("[0-9A-HJKMNP-TV-Z]{4}(?:-[0-9A-HJKMNP-TV-Z]{4}){3}")
                || decoded.length != 32 || digest.contains("=")
                || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(digest)) {
                throw new IllegalArgumentException("Invalid replay entry");
            }
            records.add(new ReplayRecord(fingerprint, digest, Instant.parse(entry.get("seenAt").getAsString())));
        }
        return records;
    }

    private LoadedReplay restore(List<ReplayRecord> records, String recoveredNotice) {
        try {
            write(records);
            notice = recoveredNotice;
            return new LoadedReplay(records, true, notice);
        } catch (RuntimeException exception) {
            healthy = false;
            notice = "cipherchannels.notice.replay.write_failed";
            return new LoadedReplay(records, false, notice);
        }
    }

    private void write(List<ReplayRecord> records) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonArray entries = new JsonArray();
        for (ReplayRecord record : records) {
            JsonObject entry = new JsonObject();
            entry.addProperty("fingerprint", record.fingerprint());
            entry.addProperty("digest", record.digest());
            entry.addProperty("seenAt", record.seenAt().toString());
            entries.add(entry);
        }
        root.add("entries", entries);
        byte[] bytes = root.toString().getBytes(StandardCharsets.UTF_8);
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), ".cipherchannels-replay-", ".tmp");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.write(ByteBuffer.wrap(bytes));
                channel.force(true);
            }
            if (Files.isRegularFile(file)) {
                Files.copy(file, backupFile(), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            forceDirectory(file.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save replay history", exception);
        } finally {
            java.util.Arrays.fill(bytes, (byte) 0);
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    temporary.toFile().deleteOnExit();
                }
            }
        }
    }

    private Path backupFile() {
        return file.resolveSibling(FILE_NAME + ".bak");
    }

    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
        }
    }

    private static void preserve(Path source) {
        if (!Files.exists(source)) {
            return;
        }
        Path target = source.resolveSibling(source.getFileName() + ".corrupt-" + Instant.now().toEpochMilli());
        try {
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(source, target);
            }
        } catch (IOException ignored) {
        }
    }

    public record LoadedReplay(List<ReplayRecord> records, boolean healthy, String notice) {
        public LoadedReplay {
            records = List.copyOf(records);
        }
    }
}
