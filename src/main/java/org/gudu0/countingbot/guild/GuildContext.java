package org.gudu0.countingbot.guild;

import org.gudu0.countingbot.config.GuildConfig;
import org.gudu0.countingbot.config.TypedConfigStore;
import org.gudu0.countingbot.counting.StateStore;
import org.gudu0.countingbot.goals.GoalsStore;
import org.gudu0.countingbot.util.BotPaths;
import org.gudu0.countingbot.util.ConsoleLog;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Everything that is "per guild" lives here.
 * <p>
 * NOTE: This is intentionally minimal for now.
 * We will wire real services later (CountingService/GoalsService etc).
 */
public final class GuildContext {

    public final long guildId;

    // Per-guild config + store
    public final TypedConfigStore<GuildConfig> configStore;
    public final GuildConfig cfg;

    // Per-guild stores (per your plan)
    public final StateStore stateStore;
    public final GoalsStore goalsStore;

    // Runtime toggles that SafetyChecks may change per guild (later)
    public final AtomicBoolean enforceDeleteRuntime;

    public GuildContext(long guildId) {
        this.guildId = guildId;

        Path dir = BotPaths.guildDir(guildId);
        ConsoleLog.info("GuildContext", "Initializing guild context dir=" + dir);

        // Load per-guild config
        Path cfgPath = dir.resolve("config.json");
        this.configStore = new TypedConfigStore<>(cfgPath, GuildConfig.class, GuildConfig::new);
        this.cfg = configStore.cfg();

        // Initialize per-guild state stores
        this.stateStore = new StateStore(dir.resolve("state.json"));
        this.goalsStore = new GoalsStore(dir.resolve("goals.json"));

        // Start autoflush on the per-guild stores (same cadence as before for now)
        this.stateStore.startAutoFlush(5);
        this.goalsStore.startAutoFlush(10);

        // Runtime toggle mirrors config at startup
        this.enforceDeleteRuntime = new AtomicBoolean(cfg.enforceDelete);

        // Helpful log for sanity
        ConsoleLog.info(
                "GuildContext",
                "Loaded guild cfg guildId=" + guildId
                        + " countingChannelId=" + cfg.countingChannelId
                        + " delay=" + cfg.countingDelaySeconds
                        + " enforceDelete=" + cfg.enforceDelete
                        + " enableLogs=" + cfg.enableLogs
                        + " logThreadId=" + cfg.logThreadId
        );
    }
}
