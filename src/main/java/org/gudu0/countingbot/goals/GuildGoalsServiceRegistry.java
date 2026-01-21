package org.gudu0.countingbot.goals;

import net.dv8tion.jda.api.JDA;
import org.gudu0.countingbot.guild.GuildContext;
import org.gudu0.countingbot.guild.GuildManager;
import org.gudu0.countingbot.util.ConsoleLog;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazy per-guild GoalsService registry.
 * <p>
 * - Goals are per guild (stored under data/guilds/<guildId>/goals.json)
 * - Each guild gets its own scheduler + pinned/edited goal embed
 * - We only create a GoalsService when we actually need it (command / goal progress / resync).
 */
public final class GuildGoalsServiceRegistry {

    private final GuildManager guilds;
    private final ConcurrentHashMap<Long, GoalsService> map = new ConcurrentHashMap<>();

    // Attached at runtime once JDA is ready
    private volatile JDA jda;

    public GuildGoalsServiceRegistry(GuildManager guilds) {
        this.guilds = guilds;
    }

    /**
     * Call once on startup after JDA is built.
     * Existing GoalsService instances (if any) will be attached.
     */
    public void attach(JDA jda) {
        this.jda = jda;
        for (Map.Entry<Long, GoalsService> e : map.entrySet()) {
            try {
                e.getValue().attach(jda);
            } catch (Exception ex) {
                ConsoleLog.error("GoalsRegistry", "Failed to attach existing GoalsService guildId=" + e.getKey() + ": " + ex.getMessage(), ex);
            }
        }
    }

    /** Get or create the per-guild GoalsService. */
    public GoalsService getOrCreate(long guildId) {
        return map.computeIfAbsent(guildId, gid -> {
            if (ConsoleLog.DEBUG) {
                ConsoleLog.debug("GoalsRegistry", "Creating GoalsService for guildId=" + gid);
            }
            GuildContext ctx = guilds.get(gid);
            GoalsService gs = new GoalsService(ctx.cfg, ctx.goalsStore, ctx.stateStore);

            JDA j = this.jda;
            if (j != null) {
                gs.attach(j);
            }

            return gs;
        });
    }

    /** Mark dirty if the service exists (do not create new schedulers unless needed). */
    public void markDirtyIfExists(long guildId) {
        GoalsService gs = map.get(guildId);
        if (gs != null) gs.markDirty();
    }

    /** Mark dirty and create the service if missing. */
    public void markDirtyOrCreate(long guildId) {
        getOrCreate(guildId).markDirty();
    }
}
