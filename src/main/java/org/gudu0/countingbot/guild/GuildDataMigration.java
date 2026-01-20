package org.gudu0.countingbot.guild;

import org.gudu0.countingbot.util.BotPaths;
import org.gudu0.countingbot.util.ConsoleLog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * One-time migration of legacy single-guild files into per-guild folder.
 * <p>
 * We ONLY copy if the destination doesn't exist (or is zero-length) to avoid overwriting real data.
 */
public final class GuildDataMigration {
    private GuildDataMigration() {}

    public static void migrateLegacyGuildFilesIfNeeded(long guildId) {
        Path guildDir = BotPaths.guildDir(guildId);

        // Legacy locations (current bot)
        Path legacyState = BotPaths.DATA.resolve("state.json");
        Path legacyGoals = BotPaths.DATA.resolve("goals.json");

        // New locations (multi-guild)
        Path newState = guildDir.resolve("state.json");
        Path newGoals = guildDir.resolve("goals.json");

        migrateOne("state", legacyState, newState);
        migrateOne("goals", legacyGoals, newGoals);
    }

    private static void migrateOne(String name, Path src, Path dst) {
        try {
            if (!Files.exists(src)) {
                ConsoleLog.debug("GuildDataMigration", "Legacy " + name + " file not found: " + src);
                return;
            }

            boolean dstExists = Files.exists(dst);
            long dstSize = dstExists ? Files.size(dst) : 0;

            // Only migrate if destination is missing or empty (common case you saw)
            if (dstExists && dstSize > 2) {
                ConsoleLog.info("GuildDataMigration", "Guild " + name + " already exists (kept): " + dst);
                return;
            }

            Files.createDirectories(dst.getParent());
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

            ConsoleLog.warn("GuildDataMigration",
                    "Migrated legacy " + name + " -> " + dst + " (source=" + src + ")");
        } catch (Exception e) {
            ConsoleLog.error("GuildDataMigration",
                    "Failed migrating " + name + " (" + src + " -> " + dst + "): " + e.getMessage(), e);
        }
    }
}
