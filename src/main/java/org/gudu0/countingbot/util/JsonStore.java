package org.gudu0.countingbot.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;

public class JsonStore<T> {
    public final Object lock = new Object();

    private final Path path;
    private final ObjectMapper om;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Class<T> type;
    private final java.util.function.Supplier<T> defaultSupplier;
    private final String nameForLogs;

    private volatile boolean dirty = false;
    @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
    private T value;

    public JsonStore(Path path, Class<T> type, java.util.function.Supplier<T> defaultSupplier, String nameForLogs) {
        this.path = path;
        this.type = type;
        this.defaultSupplier = defaultSupplier;
        this.nameForLogs = nameForLogs;

        this.om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.value = loadOrNew();
        ConsoleLog.info("JsonStore", "Loaded " + nameForLogs + " from " + path.toString());
    }

    public T get() {
        return value;
    }

    public void markDirty() {
        dirty = true;
    }

    public void startAutoFlush(long periodSeconds) {
        scheduler.scheduleAtFixedRate(this::tryFlush, periodSeconds, periodSeconds, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { flushNow(); } catch (Exception ignored) {}
            scheduler.shutdown();
        }));
    }

    public void tryFlush() {
        if (!dirty) return;
        try {
            flushNow();
        } catch (Exception e) {
            ConsoleLog.error("JsonStore", nameForLogs + " flush failed: " + e.getMessage(), e);
        }
    }

    public void flushNow() throws IOException {
        synchronized (lock) {
            if (!dirty) return;

            ConsoleLog.debug("JsonStore", "Flushing " + nameForLogs + " -> " + path);

            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");

            om.writeValue(tmp.toFile(), value);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            dirty = false;

            ConsoleLog.debug("JsonStore", "Flushed " + nameForLogs);
        }
    }

    private T loadOrNew() {
        try {
            if (Files.exists(path)) {
                return om.readValue(path.toFile(), type);
            } else {
                ConsoleLog.warn("JsonStore", nameForLogs + " missing, creating default at " + path);
            }
        } catch (Exception e) {
            ConsoleLog.error("JsonStore", "Failed to load " + nameForLogs + ", starting fresh: " + e.getMessage(), e);
        }
        return defaultSupplier.get();
    }
}
