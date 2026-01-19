package org.gudu0.countingbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.gudu0.countingbot.util.ConsoleLog;

import java.nio.file.*;

/**
 * Loads and saves {@link BotConfig} (data/config.json) with atomic writes.
 */
public class ConfigStore {
    private final Path path;
    private final ObjectMapper om;
    @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
    private BotConfig cfg;

    public ConfigStore(Path path) {
        this.path = path;
        this.om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.cfg = loadOrNew();
    }

    public BotConfig cfg() {
        return cfg;
    }

    public synchronized void save() throws Exception {
        Files.createDirectories(path.getParent());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        om.writeValue(tmp.toFile(), cfg);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        ConsoleLog.info("ConfigStore", "Saved config to " + path);
    }

    private BotConfig loadOrNew() {
        try {
            if (Files.exists(path)) {
                ConsoleLog.info("ConfigStore", "Loaded Config file from " + path);
                return om.readValue(path.toFile(), BotConfig.class);
            }
        } catch (Exception e) {
            ConsoleLog.error("ConfigStore", "Failed to load config.json, using defaults: " + e.getMessage(), e);
        }
        return new BotConfig();
    }

    /**
     * Writes a minimal template config if none exists.
     */
    public static void writeTemplateIfMissing(Path path) {
        try {
            if (Files.exists(path)) return;
            Files.createDirectories(path.getParent());
            BotConfig cfg = new BotConfig();
            cfg.guildId = "PUT_GUILD_ID_HERE";
            cfg.countingChannelId = "PUT_COUNTING_CHANNEL_ID_HERE";
            cfg.countingDelaySeconds = 2;
            cfg.enforceDelete = false;
            cfg.enableLogs = true;
            cfg.disconnectThreadId = "PUT_LOG_THREAD_ID_HERE";
            cfg.logThreadId = "PUT_LOG_THREAD_ID_HERE";
            cfg.suggestionsNotifyUserId = "PUT_USER_TO_DM_HERE_ON_SUGGESTION";
            cfg.suggestionsThreadId = "PUT_THREAD_TO_LOG_SUGGESTIONS_HERE";
            ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            om.writeValue(path.toFile(), cfg);
            ConsoleLog.warn("ConfigStore", "Missing Config, wrote template to " + path);
        } catch (Exception e) {
            ConsoleLog.error("ConfigStore", "Failed to write config template: " + e.getMessage(), e);
        }
    }
}
