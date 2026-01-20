package org.gudu0.countingbot.counting;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.gudu0.countingbot.achievements.AchievementTrigger;
import org.gudu0.countingbot.achievements.AchievementsService;
import org.gudu0.countingbot.goals.GuildGoalsServiceRegistry;
import org.gudu0.countingbot.guild.GuildContext;
import org.gudu0.countingbot.guild.GuildManager;
import org.gudu0.countingbot.logging.LogService;
import org.gudu0.countingbot.stats.StatsStore;
import org.gudu0.countingbot.stats.UserStats;
import org.gudu0.countingbot.util.BotPaths;
import org.gudu0.countingbot.util.ConsoleLog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class CountingListener extends ListenerAdapter {

    private static final int RESYNC_HISTORY = 10;

    private final GuildManager guilds;
    private final StatsStore stats;
    private final LogService logs;
    private final GuildGoalsServiceRegistry goalsRegistry;
    private final AchievementsService achievements;

    public CountingListener(GuildManager guilds,
                            StatsStore stats,
                            LogService logs,
                            GuildGoalsServiceRegistry goalsRegistry,
                            AchievementsService achievements) {
        this.guilds = guilds;
        this.stats = stats;
        this.logs = logs;
        this.goalsRegistry = goalsRegistry;
        this.achievements = achievements;
    }

    @Override
    public void onReady(ReadyEvent event) {
        // On boot: resync ONLY guilds that already have data/guilds/<id>/ on disk.
        // (This avoids creating folders for every guild automatically.)
        List<Long> configured = listGuildDirsOnDisk();

        if (configured.isEmpty()) {
            ConsoleLog.warn("Resync", "No configured guild folders found in " + BotPaths.GUILDS_DIR + " — skipping boot resync.");
            return;
        }

        ConsoleLog.info("Resync", "Boot resync for configuredGuilds=" + configured.size());

        for (long guildId : configured) {
            if (event.getJDA().getGuildById(guildId) == null) {
                ConsoleLog.warn("Resync", "Guild folder exists but bot is not in guildId=" + guildId + " — skipping.");
                continue;
            }

            GuildContext ctx = guilds.get(guildId);
            if (ctx.cfg.countingChannelId == null || ctx.cfg.countingChannelId.isBlank()) {
                ConsoleLog.warn("Resync", "guildId=" + guildId + " has no countingChannelId configured — skipping.");
                continue;
            }

            resyncNow(event.getJDA(), guildId, r -> {
                if (r.found()) {
                    ConsoleLog.info("Resync", "guildId=" + guildId + " init: last=" + r.number() + " user=" + r.userId());
                } else {
                    ConsoleLog.warn("Resync", "guildId=" + guildId + " init: no valid count found");
                }
            });
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;
        if (event.getAuthor().isBot()) return;

        long guildId = event.getGuild().getIdLong();
        GuildContext ctx = guilds.get(guildId);

        // Only active if this guild configured a counting channel
        String countingChannelId = ctx.cfg.countingChannelId;
        if (countingChannelId == null || countingChannelId.isBlank()) return;

        if (!event.getChannel().getId().equals(countingChannelId)) return;

        Message msg = event.getMessage();
        Parsed parsed = parseStrictCount(msg);

        if (parsed == null) {
            // Not a strict number -> invalid (delete if enforced)
            logDecision(guildId, "INVALID (not strict integer)", msg);
            markIncorrect(ctx, guildId, msg);
            if (ctx.enforceDeleteRuntime.get()) delete(guildId, msg);
            return;
        }

        // Pull state under lock (we need a consistent snapshot for rules)
        long expected;
        long lastNumber;
        long lastUserId;
        Long lastTime;

        synchronized (ctx.stateStore.lock) {
            CountingState st = ctx.stateStore.state();
            lastNumber = st.lastNumber;
            lastUserId = st.lastUserId;
            expected = st.lastNumber + 1;
            lastTime = st.userLastValidCountAt.get(parsed.authorId);
        }

        // init start behavior
        if (lastNumber == -1) {
            accept(ctx, guildId, parsed, msg, "ACCEPT (init start)");
            return;
        }

        // Wrong number
        if (parsed.number != expected) {
            logDecision(guildId, "INVALID (expected " + expected + ", got " + parsed.number + ")", msg);

            // Saboteur hook: someone caused someone else to fail (same as before)
            if (parsed.number == lastNumber && lastUserId != 0 && parsed.authorId != lastUserId) {
                achievements.unlockById(guildId, lastUserId, "cause_fail");
            }

            markIncorrect(ctx, guildId, msg);
            if (ctx.enforceDeleteRuntime.get()) delete(guildId, msg);
            return;
        }

        // Same user twice
        if (parsed.authorId == lastUserId) {
            logDecision(guildId, "INVALID (same user twice)", msg);
            markIncorrect(ctx, guildId, msg);
            if (ctx.enforceDeleteRuntime.get()) delete(guildId, msg);
            return;
        }

        // Cooldown (only between VALID counts)
        long now = System.currentTimeMillis();
        if (lastTime != null) {
            long minGapMs = ctx.cfg.countingDelaySeconds * 1000L;
            if (now - lastTime < minGapMs) {
                logDecision(guildId, "INVALID (cooldown " + ctx.cfg.countingDelaySeconds + "s)", msg);

                // Not shaming cooldown violations (by design)
                if (ctx.enforceDeleteRuntime.get()) delete(guildId, msg);
                return;
            }
        }

        accept(ctx, guildId, parsed, msg, "ACCEPT");
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        if (event.getGuild() == null) return;

        long guildId = event.getGuild().getIdLong();
        GuildContext ctx = guilds.get(guildId);

        String countingChannelId = ctx.cfg.countingChannelId;
        if (countingChannelId == null || countingChannelId.isBlank()) return;

        if (!event.getChannel().getId().equals(countingChannelId)) return;

        long deletedId = event.getMessageIdLong();

        boolean shouldResync;
        synchronized (ctx.stateStore.lock) {
            long lastMsgId = ctx.stateStore.state().lastMessageId;
            shouldResync = (lastMsgId == 0 || deletedId == lastMsgId);
        }

        if (!shouldResync) return;

        ConsoleLog.warn("Counting", "guildId=" + guildId + " last count message deleted (or unknown). Resyncing...");
        logs.log(guildId, "Last accepted count was deleted; global streak reset and resync triggered.");

        synchronized (ctx.stateStore.lock) {
            ctx.stateStore.state().globalStreakCurrent = 0;
            ctx.stateStore.markDirty();
        }

        goalsRegistry.markDirtyIfExists(guildId);

        resyncNow(event.getJDA(), guildId, r -> {
            if (r.found()) {
                ConsoleLog.info("Resync", "guildId=" + guildId + " post-delete: last=" + r.number() + " user=" + r.userId());
            } else {
                ConsoleLog.warn("Resync", "guildId=" + guildId + " post-delete: no valid count found");
            }
        });
    }

    // ----------------------------
    // Core actions
    // ----------------------------

    private void accept(GuildContext ctx, long guildId, Parsed parsed, Message msg, String reason) {
        long now = System.currentTimeMillis();

        // Update counting state (guild-local)
        synchronized (ctx.stateStore.lock) {
            CountingState st = ctx.stateStore.state();
            st.lastNumber = parsed.number;
            st.lastUserId = parsed.authorId;
            st.lastMessageId = msg.getIdLong();
            st.userLastValidCountAt.put(parsed.authorId, now);

            st.globalStreakCurrent++;
            if (st.globalStreakCurrent > st.globalStreakBest) st.globalStreakBest = st.globalStreakCurrent;

            ctx.stateStore.markDirty();
        }

        // Update global stats (bot-wide)
        synchronized (stats.lock) {
            UserStats us = stats.data().getOrCreate(parsed.authorId);
            us.onCorrect(now);
            us.posCounts++;
            stats.markDirty();
        }

        achievements.onTrigger(AchievementTrigger.VALID_COUNT, guildId, parsed.authorId);

        // Goal winner check (guild-local goal)
        synchronized (ctx.goalsStore.lock) {
            if (ctx.goalsStore.state().active && parsed.number == ctx.goalsStore.state().target) {
                achievements.unlockById(guildId, parsed.authorId, "goal_winner");
            }
        }

        goalsRegistry.markDirtyOrCreate(guildId);

        logDecision(guildId, reason, msg);
    }

    private void markIncorrect(GuildContext ctx, long guildId, Message msg) {
        long now = System.currentTimeMillis();

        synchronized (stats.lock) {
            stats.data().getOrCreate(msg.getAuthor().getIdLong()).onIncorrect(now);
            stats.markDirty();
        }

        synchronized (ctx.stateStore.lock) {
            ctx.stateStore.state().globalStreakCurrent = 0;
            ctx.stateStore.markDirty();
        }

        achievements.onTrigger(AchievementTrigger.INVALID_COUNT, guildId, msg.getAuthor().getIdLong());
        goalsRegistry.markDirtyIfExists(guildId);
    }

    private void delete(long guildId, Message msg) {
        msg.delete().queue(
                ok -> { },
                err -> ConsoleLog.error("Counting", "guildId=" + guildId + " delete failed: " + err.getMessage(), err)
        );
        logs.log(guildId, "Deleted invalid count by " + msg.getAuthor().getName() + ": " + msg.getContentRaw());
    }

    private void logDecision(long guildId, String reason, Message msg) {
        ConsoleLog.info("Counting",
                "guildId=" + guildId + " " + reason
                        + " | author=" + msg.getAuthor().getId()
                        + " name=" + msg.getAuthor().getName()
                        + " content=\"" + msg.getContentRaw() + "\"");
    }

    /**
     * Strict integer parse (matches your rules):
     * - entire content must be digits ONLY (no spaces, no extra text)
     * - no negatives
     * - no leading zeros unless "0"
     */
    private Parsed parseStrictCount(Message msg) {
        String s = msg.getContentRaw(); // DO NOT trim; whitespace is invalid
        if (s == null || s.isEmpty()) return null;

        if (!s.matches("\\d+")) return null;
        if (s.length() > 1 && s.startsWith("0")) return null;

        try {
            long n = Long.parseLong(s);
            return new Parsed(n, msg.getAuthor().getIdLong());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ----------------------------
    // Resync (guild-specific)
    // ----------------------------

    public void resyncNow(JDA jda, long guildId, Consumer<ResyncResult> cb) {
        GuildContext ctx = guilds.get(guildId);

        String channelIdStr = ctx.cfg.countingChannelId;
        if (channelIdStr == null || channelIdStr.isBlank()) {
            cb.accept(new ResyncResult(false, -1, 0, 0));
            return;
        }

        TextChannel ch;
        try {
            long channelId = Long.parseLong(channelIdStr);
            ch = jda.getTextChannelById(channelId);
        } catch (Exception e) {
            ConsoleLog.warn("Resync", "guildId=" + guildId + " invalid countingChannelId=" + channelIdStr);
            cb.accept(new ResyncResult(false, -1, 0, 0));
            return;
        }

        if (ch == null) {
            ConsoleLog.warn("Resync", "guildId=" + guildId + " counting channel not found / not visible.");
            cb.accept(new ResyncResult(false, -1, 0, 0));
            return;
        }

        ConsoleLog.info("Resync", "guildId=" + guildId + " Starting resync (retrievePast=" + RESYNC_HISTORY + ") channelId=" + channelIdStr);

        ch.getHistory().retrievePast(RESYNC_HISTORY).queue(history -> {
            Parsed found = null;
            Message foundMsg = null;

            for (Message m : history) {
                Parsed p = parseStrictCount(m);
                if (p != null) {
                    found = p;
                    foundMsg = m;
                    ConsoleLog.info("Resync", "guildId=" + guildId + " Found last valid: n=" + found.number + " userId=" + found.authorId + " msgId=" + foundMsg.getId());
                    break;
                }
            }

            if (found == null) {
                synchronized (ctx.stateStore.lock) {
                    ctx.stateStore.state().lastNumber = -1;
                    ctx.stateStore.state().lastUserId = 0;
                    ctx.stateStore.state().lastMessageId = 0;
                    ctx.stateStore.markDirty();
                }
                ConsoleLog.warn("Resync", "guildId=" + guildId + " No valid count found in recent history");
                cb.accept(new ResyncResult(false, -1, 0, 0));
                return;
            }

            synchronized (ctx.stateStore.lock) {
                ctx.stateStore.state().lastNumber = found.number;
                ctx.stateStore.state().lastUserId = found.authorId;
                ctx.stateStore.state().lastMessageId = foundMsg.getIdLong();
                ctx.stateStore.markDirty();
            }

            goalsRegistry.markDirtyOrCreate(guildId);

            cb.accept(new ResyncResult(true, found.number, found.authorId, foundMsg.getIdLong()));
        }, err -> {
            ConsoleLog.error("Resync", "guildId=" + guildId + " History fetch failed: " + err.getMessage(), err);
            cb.accept(new ResyncResult(false, -1, 0, 0));
        });
    }

    // ----------------------------
    // Disk discovery (configured guilds)
    // ----------------------------

    private static List<Long> listGuildDirsOnDisk() {
        Path base = BotPaths.GUILDS_DIR;
        if (!Files.isDirectory(base)) return List.of();

        List<Long> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(base)) {
            s.filter(Files::isDirectory).forEach(p -> {
                String name = p.getFileName().toString();
                try {
                    out.add(Long.parseLong(name));
                } catch (Exception ignored) {
                    // ignore non-numeric folders
                }
            });
        } catch (Exception e) {
            ConsoleLog.error("Resync", "Failed listing guild dirs: " + e.getMessage(), e);
        }
        return out;
    }

    private record Parsed(long number, long authorId) {}
    public record ResyncResult(boolean found, long number, long userId, long messageId) {}
}
