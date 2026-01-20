package org.gudu0.countingbot.config;

import org.gudu0.countingbot.util.BotPaths;
import org.gudu0.countingbot.util.ConsoleLog;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigMigration {
    private ConfigMigration() {}

    /**
     * One-time migration:
     * - reads legacy data/config.json (BotConfig)
     * - writes data/global/config.json (GlobalConfig)
     * - writes data/guilds/<guildId>/config.json (GuildConfig)
     * <p>
     * Safe: if new configs already exist, does nothing.
     */
    public static void migrateFromLegacyIfNeeded(BotConfig legacy) {
        try {
            Path globalCfgPath = BotPaths.GLOBAL_DIR.resolve("config.json");

            long guildId = Long.parseLong(legacy.guildId);
            Path guildCfgPath = BotPaths.guildDir(guildId).resolve("config.json");

            boolean globalExists = Files.exists(globalCfgPath);
            boolean guildExists  = Files.exists(guildCfgPath);

            if (globalExists && guildExists) {
                ConsoleLog.info("ConfigMigration", "New config files already exist; skipping migration.");
                return;
            }

            // Build new configs from legacy fields
            GlobalConfig g = new GlobalConfig();
            g.disconnectThreadId = legacy.disconnectThreadId;
            g.suggestionsThreadId = legacy.suggestionsThreadId;
            g.suggestionsNotifyUserId = legacy.suggestionsNotifyUserId;

            GuildConfig gc = new GuildConfig();
            gc.countingChannelId = legacy.countingChannelId;
            gc.countingDelaySeconds = legacy.countingDelaySeconds;
            gc.enforceDelete = legacy.enforceDelete;
            gc.enableLogs = legacy.enableLogs;
            gc.logThreadId = legacy.logThreadId;

            // Save (only if missing)
            if (!globalExists) {
                new TypedConfigStore<>(globalCfgPath, GlobalConfig.class, GlobalConfig::new).cfg().disconnectThreadId = g.disconnectThreadId;
                // ^ we want to write the full object, so do this properly:
                TypedConfigStore<GlobalConfig> gs = new TypedConfigStore<>(globalCfgPath, GlobalConfig.class, GlobalConfig::new);
                gs.cfg().disconnectThreadId = g.disconnectThreadId;
                gs.cfg().suggestionsThreadId = g.suggestionsThreadId;
                gs.cfg().suggestionsNotifyUserId = g.suggestionsNotifyUserId;
                gs.save();
            }

            if (!guildExists) {
                TypedConfigStore<GuildConfig> gcs = new TypedConfigStore<>(guildCfgPath, GuildConfig.class, GuildConfig::new);
                gcs.cfg().countingChannelId = gc.countingChannelId;
                gcs.cfg().countingDelaySeconds = gc.countingDelaySeconds;
                gcs.cfg().enforceDelete = gc.enforceDelete;
                gcs.cfg().enableLogs = gc.enableLogs;
                gcs.cfg().logThreadId = gc.logThreadId;
                gcs.save();
            }

            ConsoleLog.warn("ConfigMigration", "Migrated legacy config -> new global/guild config files.");
        } catch (Exception e) {
            ConsoleLog.error("ConfigMigration", "Migration failed (bot will still use legacy config): " + e.getMessage(), e);
        }
    }
}
