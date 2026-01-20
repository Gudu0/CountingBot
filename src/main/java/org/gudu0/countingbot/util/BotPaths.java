package org.gudu0.countingbot.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class BotPaths {
    private BotPaths() {}

    // Root data dir (same as you have now)
    public static final Path DATA = Path.of("data");

    // New structure
    public static final Path GLOBAL_DIR = DATA.resolve("global");
    public static final Path GUILDS_DIR = DATA.resolve("guilds");

    public static Path guildDir(long guildId) {
        return GUILDS_DIR.resolve(Long.toString(guildId));
    }

    public static void ensureBaseDirs() {
        try {
            Files.createDirectories(GLOBAL_DIR);
            Files.createDirectories(GUILDS_DIR);
            ConsoleLog.info("BotPaths", "Ensured data dirs: " + GLOBAL_DIR + " and " + GUILDS_DIR);
        } catch (Exception e) {
            ConsoleLog.error("BotPaths", "Failed to create base data dirs: " + e.getMessage(), e);
        }
    }
}
