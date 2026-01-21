package org.gudu0.countingbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.gudu0.countingbot.util.ConsoleLog;

import java.nio.file.*;

public class TypedConfigStore<T> {
    private final Path path;
    private final ObjectMapper om;
    private final Class<T> type;
    private final java.util.function.Supplier<T> defaults;

    private T cfg;

    public TypedConfigStore(Path path, Class<T> type, java.util.function.Supplier<T> defaults) {
        this.path = path;
        this.type = type;
        this.defaults = defaults;
        this.om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.cfg = loadOrNew();
    }

    public T cfg() { return cfg; }

    public synchronized void save() throws Exception {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        om.writeValue(tmp.toFile(), cfg);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        if (ConsoleLog.DEBUG) {
            ConsoleLog.debug("TypedConfigStore", "Saved config to " + path);
        }
    }

    private T loadOrNew() {
        try {
            if (Files.exists(path)) {
                ConsoleLog.info("TypedConfigStore", "Loaded config from " + path);
                return om.readValue(path.toFile(), type);
            }
        } catch (Exception e) {
            ConsoleLog.error("TypedConfigStore", "Failed to load " + path + ", using defaults: " + e.getMessage(), e);
        }
        ConsoleLog.warn("TypedConfigStore", "Config missing: " + path + " (will use defaults until saved)");
        return defaults.get();
    }
}
