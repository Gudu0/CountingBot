package org.gudu0.countingbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.gudu0.countingbot.achievements.AchievementsService;
import org.gudu0.countingbot.achievements.AchievementsStore;
import org.gudu0.countingbot.commands.*;
import org.gudu0.countingbot.config.BotConfig;
import org.gudu0.countingbot.config.ConfigStore;
import org.gudu0.countingbot.counting.CountingListener;
import org.gudu0.countingbot.counting.StateStore;
import org.gudu0.countingbot.disconnects.DisconnectDailyReporter;
import org.gudu0.countingbot.disconnects.DisconnectStore;
import org.gudu0.countingbot.goals.GoalsService;
import org.gudu0.countingbot.goals.GoalsStore;
import org.gudu0.countingbot.logging.LogService;
import org.gudu0.countingbot.stats.StatsStore;
import org.gudu0.countingbot.suggestions.SuggestionsService;
import org.gudu0.countingbot.suggestions.SuggestionsStore;
import org.gudu0.countingbot.util.ConsoleLog;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {

    public static void main(String[] args) throws Exception {
        ConsoleLog.info("Main", "Starting Bot");

        // 1) Token stays in env (safer than config.json)
            String token = reqEnv("DISCORD_TOKEN");

        // 2) Load config.json (guild/channel/delay/enforcement)
        Path configPath = Path.of("data/config.json");
        ConfigStore.writeTemplateIfMissing(configPath);

        ConfigStore configStore = new ConfigStore(configPath);
        BotConfig cfg = configStore.cfg();
        validateConfig(cfg);

        ConsoleLog.info("Main", "Loaded config: " + configPath);
        ConsoleLog.info(
                "Main",
                "Config: guildId=" + cfg.guildId
                        + " countingChannelId=" + cfg.countingChannelId
                        + " delay=" + cfg.countingDelaySeconds
                        + " enforceDelete=" + cfg.enforceDelete
                        + " enableLogs=" + cfg.enableLogs
        );

        // 3) Stores (JSON persistence)
        ConsoleLog.info("Main", "Initializing stores...");

        StateStore stateStore = new StateStore(Path.of("data/state.json"));
        stateStore.startAutoFlush(5);

        StatsStore statsStore = new StatsStore(Path.of("data/stats.json"));
        statsStore.startAutoFlush(10);

        DisconnectStore disconnectStore = new DisconnectStore(Path.of("data/disconnects.json"));

        GoalsStore goalsStore = new GoalsStore(Path.of("data/goals.json"));
        goalsStore.startAutoFlush(10);

        SuggestionsStore suggestionsStore = new SuggestionsStore(Path.of("data/suggestions.json"));
        suggestionsStore.startAutoFlush(10);

        AchievementsStore achievementsStore = new AchievementsStore(Path.of("data/achievements.json"));
        achievementsStore.startAutoFlush(10);

        // 4) Runtime-togglable enforcement (SafetyChecks may disable)
        AtomicBoolean enforceDelete = new AtomicBoolean(cfg.enforceDelete);

        // 5) Services (logic + Discord side effects)
        LogService logs = new LogService(cfg);

        DisconnectDailyReporter disconnectReporter = new DisconnectDailyReporter(cfg, disconnectStore);

        GoalsService goalsService = new GoalsService(cfg, goalsStore, stateStore);
        SuggestionsService suggestionsService = new SuggestionsService(cfg, suggestionsStore);

        AchievementsService achievementsService =
                new AchievementsService(achievementsStore, stateStore, statsStore, logs);

        CountingListener countingListener =
                new CountingListener(cfg, stateStore, statsStore, enforceDelete, logs, goalsService, achievementsService);

        // 6) Build JDA
        ConsoleLog.info("Main", "Building JDA (MESSAGE_CONTENT enabled)");
        JDA jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(
                        // Commands
                        new PingListener(),
                        new StatsListener(statsStore),
                        new LeaderboardListener(statsStore, stateStore),
                        new CountDelayListener(cfg, configStore),
                        new ResyncListener(countingListener),
                        new GoalCommandListener(goalsService),
                        new SuggestCommandListener(suggestionsService),
                        new AchievementsCommandListener(achievementsService),

                        // Core listeners
                        countingListener,
                        disconnectReporter
                )
                .build();

        jda.awaitReady();
        ConsoleLog.info("Main", "JDA ready as " + jda.getSelfUser().getName());

        // 7) Attach services that need JDA
        ConsoleLog.info("Main", "Attaching services");
        logs.attach(jda);
        goalsService.attach(jda);
        suggestionsService.attach(jda);

        // 8) Safety checks (may disable enforceDelete if perms missing)
        ConsoleLog.info("Main", "Running safety checks");
        SafetyChecks.run(jda, cfg, enforceDelete);

        // 9) Register guild commands
        ConsoleLog.info("Main", "Registering guild commands");
        registerGuildCommands(jda, cfg);

        ConsoleLog.info("Main", "Startup complete");

        logs.log("Bot Startup Completed Successfully.");
    }

    private static void registerGuildCommands(JDA jda, BotConfig cfg) {
        Objects.requireNonNull(jda.getGuildById(cfg.guildId), "Guild not found: " + cfg.guildId)
                .updateCommands()
                .addCommands(
                        Commands.slash("ping", "Replies with pong"),

                        Commands.slash("stats", "Show counting stats (fame/shame)")
                                .addOption(OptionType.USER, "user", "User to view (defaults to you)", false),

                        Commands.slash("leaderboard", "Top fame/shame + global streak"),

                        Commands.slash("countdelay", "Set counting cooldown delay (seconds)")
                                .addOption(OptionType.INTEGER, "seconds", "Cooldown between VALID counts", true),

                        Commands.slash("resync", "Sync count to the counting channel history"),

                        Commands.slash("goal", "Manage the counting goal")
                                .addSubcommands(
                                        new SubcommandData("set", "Set a new goal")
                                                .addOption(OptionType.INTEGER, "target", "Goal target number (e.g. 2000)", true)
                                                .addOption(OptionType.STRING, "deadline", "Optional: none | in 7d | 2026-01-14", false),
                                        new SubcommandData("clear", "Clear the current goal"),
                                        new SubcommandData("view", "View the current goal")
                                ),

                        Commands.slash("suggest", "Submit a suggestion for the bot!")
                                .addOption(OptionType.STRING, "text", "Your suggestion", true),

                        Commands.slash("achievements", "View achievements")
                                .addOption(OptionType.USER, "user", "User to view (defaults to you)", false)
                )
                .queue(
                        ok -> ConsoleLog.info("Main", "Guild slash commands registered/updated"),
                        err -> ConsoleLog.error("Main", "Failed to register guild slash commands: " + err.getMessage(), err)
                );
    }

    @SuppressWarnings("SameParameterValue")
    private static String reqEnv(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing env var: " + key);
        }
        return v.trim();
    }

    private static void validateConfig(BotConfig cfg) {
        if (cfg.guildId == null || cfg.guildId.isBlank() || cfg.guildId.startsWith("PUT_")) {
            throw new IllegalStateException("data/config.json: guildId is missing or placeholder");
        }
        if (cfg.countingChannelId == null || cfg.countingChannelId.isBlank() || cfg.countingChannelId.startsWith("PUT_")) {
            throw new IllegalStateException("data/config.json: countingChannelId is missing or placeholder");
        }
        if (cfg.countingDelaySeconds < 0) {
            throw new IllegalStateException("data/config.json: countingDelaySeconds must be >= 0");
        }
    }
}
