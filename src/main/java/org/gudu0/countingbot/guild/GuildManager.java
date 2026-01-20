package org.gudu0.countingbot.guild;

import org.gudu0.countingbot.util.ConsoleLog;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches and serves GuildContext objects.
 * <p>
 * This is the main "router" dependency you'll inject into listeners later.
 */
public final class GuildManager {

    private final ConcurrentHashMap<Long, GuildContext> contexts = new ConcurrentHashMap<>();

    /**
     * Load or retrieve a cached GuildContext.
     * Logs cache hits/misses for easy debugging.
     */
    public GuildContext get(long guildId) {
        GuildContext existing = contexts.get(guildId);
        if (existing != null) {
            ConsoleLog.debug("GuildManager", "Cache hit guildId=" + guildId);
            return existing;
        }

        ConsoleLog.info("GuildManager", "Cache miss guildId=" + guildId + " (creating context)");
        GuildContext created = new GuildContext(guildId);

        GuildContext raced = contexts.putIfAbsent(guildId, created);
        if (raced != null) {
            ConsoleLog.warn("GuildManager", "Race: another thread created context first guildId=" + guildId);
            return raced;
        }

        ConsoleLog.info("GuildManager", "Context ready guildId=" + guildId);
        return created;
    }

    /**
     * Optional helper for debugging.
     */
    public int cachedCount() {
        return contexts.size();
    }
}
