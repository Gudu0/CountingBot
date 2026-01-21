package org.gudu0.countingbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.gudu0.countingbot.achievements.AchievementsService;
import org.gudu0.countingbot.achievements.AchievementsStore;
import org.gudu0.countingbot.commands.*;
import org.gudu0.countingbot.config.GlobalConfig;
import org.gudu0.countingbot.config.TypedConfigStore;
import org.gudu0.countingbot.counting.CountingListener;
import org.gudu0.countingbot.disconnects.DisconnectDailyReporter;
import org.gudu0.countingbot.disconnects.DisconnectStore;
import org.gudu0.countingbot.goals.GuildGoalsServiceRegistry;
import org.gudu0.countingbot.guild.GuildContext;
import org.gudu0.countingbot.guild.GuildJoinListener;
import org.gudu0.countingbot.guild.GuildManager;
import org.gudu0.countingbot.logging.LogService;
import org.gudu0.countingbot.stats.StatsStore;
import org.gudu0.countingbot.suggestions.SuggestionsService;
import org.gudu0.countingbot.suggestions.SuggestionsStore;
import org.gudu0.countingbot.util.BotPaths;
import org.gudu0.countingbot.util.ConsoleLog;

import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws Exception {
        ConsoleLog.info("Main", "Starting Bot");
        BotPaths.ensureBaseDirs();

        // 1) Token (env)
        String token = reqEnv("DISCORD_TOKEN");

        // 2) Optional: legacy migration (safe, runs only if legacy files exist and parse)
//        tryLegacyBootstrap();

        // 3) Load global config (data/global/config.json)
        GlobalConfig globalCfg = loadOrCreateGlobalConfig();

        if (ConsoleLog.DEBUG) {
            ConsoleLog.debug("Main", "GlobalConfig: disconnectThreadId=" + safe(globalCfg.disconnectThreadId)
                    + " suggestionsNotifyUserId=" + safe(globalCfg.suggestionsNotifyUserId));
        }

        // 4) Global stores live in data/global/
        if (ConsoleLog.DEBUG) {
            ConsoleLog.debug("Main", "Initializing GLOBAL stores (data/global/*) ...");
        }

        StatsStore statsStore = new StatsStore(BotPaths.GLOBAL_DIR.resolve("stats.json"));
        statsStore.startAutoFlush(10);

        SuggestionsStore suggestionsStore = new SuggestionsStore(BotPaths.GLOBAL_DIR.resolve("suggestions.json"));
        suggestionsStore.startAutoFlush(10);

        AchievementsStore achievementsStore = new AchievementsStore(BotPaths.GLOBAL_DIR.resolve("achievements.json"));
        achievementsStore.startAutoFlush(10);

        DisconnectStore disconnectStore = new DisconnectStore(BotPaths.GLOBAL_DIR.resolve("disconnects.json"));
        disconnectStore.startAutoFlush(30);

        // 5) Multi-guild router
        GuildManager guilds = new GuildManager();

        // 6) Services (guild-aware where needed)
        LogService logs = new LogService(guilds);

        GuildGoalsServiceRegistry goalsRegistry = new GuildGoalsServiceRegistry(guilds);

        SuggestionsService suggestionsService = new SuggestionsService(globalCfg, suggestionsStore);

        AchievementsService achievementsService = new AchievementsService(achievementsStore, guilds, statsStore, logs);

        CountingListener countingListener = new CountingListener(guilds, statsStore, logs, goalsRegistry, achievementsService);

        DisconnectDailyReporter disconnectReporter = new DisconnectDailyReporter(globalCfg, disconnectStore);

        // 7) Build JDA
        ConsoleLog.info("Main", "Building JDA (MESSAGE_CONTENT enabled)");
        JDA jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(
                        // Commands (now routed per guild where needed)
                        new PingListener(),
                        new StatsListener(statsStore),
                        new LeaderboardListener(statsStore, guilds),
                        new CountDelayListener(guilds),
                        new ResyncListener(countingListener),
                        new GoalCommandListener(guilds, goalsRegistry),
                        new SuggestCommandListener(suggestionsService),
                        new AchievementsCommandListener(achievementsService),
                        new SetupListener(guilds),
                        new GuildJoinListener(guilds, (j, guild) -> registerGuildCommandsOne(guild)),
                        // Core listeners
                        countingListener,
                        disconnectReporter
                )
                .build();

        jda.awaitReady();
        ConsoleLog.info("Main", "JDA ready as " + jda.getSelfUser().getName());

        // 8) Attach services that need JDA
        if (ConsoleLog.DEBUG) {
            ConsoleLog.debug("Main", "Attaching services");
        }
        logs.attach(jda);
        goalsRegistry.attach(jda);
        suggestionsService.attach(jda);

        // 9) Safety checks (PER GUILD; will disable enforceDelete per guild if perms missing)
        if (ConsoleLog.DEBUG) {
            ConsoleLog.debug("Main", "Running safety checks (per guild)");
        }
        for (Guild g : jda.getGuilds()) {
            GuildContext ctx = guilds.get(g.getIdLong());
            SafetyChecks.runForGuild(jda, ctx);
        }

        // 10) Register commands (guild-scoped, for every guild)
        if (ConsoleLog.DEBUG) {
            ConsoleLog.debug("Main", "Registering guild commands (all guilds)");
        }
        registerGuildCommandsAll(jda);

        ConsoleLog.info("Main", "Startup complete");
        logs.logGlobal("Bot Startup Completed Successfully.");
    }


    private static GlobalConfig loadOrCreateGlobalConfig() {
        try {
            Path p = BotPaths.GLOBAL_DIR.resolve("config.json");
            boolean existed = Files.exists(p);

            TypedConfigStore<GlobalConfig> s = new TypedConfigStore<>(p, GlobalConfig.class, GlobalConfig::new);

            // TypedConfigStore loads defaults but DOES NOT write by itself.
            if (!existed) {
                ConsoleLog.warn("Main", "Global config missing; creating default at " + p);
                s.save();
            }

            return s.cfg();
        } catch (Exception e) {
            ConsoleLog.error("Main", "Failed to load GlobalConfig; using defaults. " + e.getMessage(), e);
            return new GlobalConfig();
        }
    }

    // ----------------------------
    // Commands registration
    // ----------------------------

    private static void registerGuildCommandsAll(JDA jda) {
        for (Guild g : jda.getGuilds()) {
            registerGuildCommandsOne(g);
        }
    }
    private static void registerGuildCommandsOne(Guild g) {
        g.updateCommands()
                .addCommands(
                        Commands.slash("ping", "Replies with pong"),

                        Commands.slash("stats", "Show counting stats (fame/shame)")
                                .addOption(OptionType.USER, "user", "User to view (defaults to you)", false),

                        Commands.slash("leaderboard", "Top fame/shame + (this guild) global streak"),

                        Commands.slash("countdelay", "Set counting cooldown delay (seconds)")
                                .addOption(OptionType.INTEGER, "seconds", "Cooldown between VALID counts", true),

                        Commands.slash("resync", "Sync count to the counting channel history (this guild)"),

                        Commands.slash("goal", "Manage the counting goal (this guild)")
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
                                .addOption(OptionType.USER, "user", "User to view (defaults to you)", false),
                        Commands.slash("setup", "Configure this bot for this server (admin only)")
                                .addSubcommands(
                                        new SubcommandData("status", "Show current config for this server"),

                                        new SubcommandData("setcountingchannel", "Set the counting channel")
                                                .addOption(OptionType.CHANNEL, "channel", "The #counting channel", true),

                                        new SubcommandData("setdelay", "Set the cooldown delay (seconds) between VALID counts")
                                                .addOption(OptionType.INTEGER, "seconds", "Cooldown seconds (>= 0)", true),

                                        new SubcommandData("setenforcedelete", "Enable/disable deleting invalid counts")
                                                .addOption(OptionType.BOOLEAN, "enabled", "true=delete invalid counts", true),

                                        new SubcommandData("setenablelogs", "Enable/disable per-guild logging")
                                                .addOption(OptionType.BOOLEAN, "enabled", "true=send logs to log thread", true),

                                        new SubcommandData("setlogthread", "Set the log channel/thread to send logs to")
                                                .addOption(OptionType.CHANNEL, "channel", "A thread or text channel", true)
                                )
                )
                .queue(
                        ok -> registerDebugOkMessage(g),
                        err -> ConsoleLog.error("Main", "Failed registering commands in guildId=" + g.getId() + ": " + err.getMessage(), err)
                );
    }

    // ----------------------------
    // Utils
    // ----------------------------

    private static void registerDebugOkMessage(Guild g){
        if (ConsoleLog.DEBUG) {
            ConsoleLog.debug("Main", "Guild commands updated: " + g.getName() + " (" + g.getId() + ")");
        }
    }

    private static String reqEnv(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) throw new IllegalStateException("Missing environment variable: " + key);
        return v;
    }

    private static String safe(String s) {
        return (s == null ? "" : s);
    }
}
