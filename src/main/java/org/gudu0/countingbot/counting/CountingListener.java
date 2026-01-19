package org.gudu0.countingbot.counting;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import org.gudu0.countingbot.achievements.AchievementTrigger;
import org.gudu0.countingbot.achievements.AchievementsService;
import org.gudu0.countingbot.config.BotConfig;
import org.gudu0.countingbot.goals.GoalsService;
import org.gudu0.countingbot.logging.LogService;
import org.gudu0.countingbot.stats.StatsStore;
import org.gudu0.countingbot.stats.UserStats;
import org.gudu0.countingbot.util.ConsoleLog;


import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class CountingListener extends ListenerAdapter {
    private final BotConfig config;
    private final StateStore store;
    private final StatsStore stats;
    private final LogService logs;
    private final GoalsService goals;
    private final AchievementsService achievements;

    private final AtomicBoolean enforceDelete;

    public CountingListener(BotConfig config, StateStore store, StatsStore stats, AtomicBoolean enforceDelete, LogService logs, GoalsService goals, AchievementsService achievements) {
        this.config = config;
        this.store = store;
        this.stats = stats;
        this.enforceDelete = enforceDelete;
        this.logs = logs;
        this.goals = goals;
        this.achievements = achievements;
    }

    @Override
    public void onReady(ReadyEvent event) {
        resyncNow(event.getJDA(), r -> {
            if (r.found()){
                ConsoleLog.info("Resync", "Init state: last=" + r.number() + " user=" + r.userId());
            }
            else {
                ConsoleLog.warn("Resync", "Init state: no valid count found");
            }
        });
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bots/webhooks
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;

        // Only counting channel
        if (!event.getChannel().getId().equals(config.countingChannelId)) return;

        Message msg = event.getMessage();
        Parsed parsed = parseStrictCount(msg);

        if (parsed == null) {
            // Not a strict number message -> should be deleted later
            logDecision("INVALID (not strict integer)", msg);
            markIncorrect(msg);
            if (enforceDelete.get()) delete(msg);
            return;
        }

        // Rules:
        // - must be exactly lastNumber + 1
        // - cannot be same user twice in a row
        // - per-user delay between VALID counts
        CountingState state = store.state();

        long expected = state.lastNumber + 1;
        if (state.lastNumber == -1) {
            // If fresh/unknown, accept any number as starting point? Or require 1?
            // Current JS behavior depends on saved state; here we accept as start.
            accept(parsed, msg, "ACCEPT (init start)");
            return;
        }

        if (parsed.number != expected) {
            logDecision("INVALID (expected " + expected + ", got " + parsed.number + ")", msg);
            if (parsed.number == state.lastNumber
                    && state.lastUserId != 0
                    && parsed.authorId != state.lastUserId) {
                achievements.unlockById(msg.getGuild().getIdLong(), state.lastUserId, "cause_fail");
            }
            markIncorrect(msg);
            if (enforceDelete.get()) delete(msg);
            return;
        }

        if (parsed.authorId == state.lastUserId) {
            logDecision("INVALID (same user twice)", msg);
            markIncorrect(msg);
            if (enforceDelete.get()) delete(msg);
            return;
        }

        long now = System.currentTimeMillis();
        Long lastTime = state.userLastValidCountAt.get(parsed.authorId);
        if (lastTime != null) {
            long minGapMs = config.countingDelaySeconds * 1000L;
            if (now - lastTime < minGapMs) {
                logDecision("INVALID (cooldown " + config.countingDelaySeconds + "s)", msg);
                // Not marking incorrect for sending too fast, being nice.
//                markIncorrect(msg);
                if (enforceDelete.get()) delete(msg);
                return;
            }
        }

        accept(parsed, msg, "ACCEPT");
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        // Only counting channel
        if (!event.getChannel().getId().equals(config.countingChannelId)) return;

        long deletedId = event.getMessageIdLong();
        CountingState state = store.state();

        // Only resync if the deleted message was the last accepted count
        // (or we don't know what the last message id is yet)
        if (state.lastMessageId != 0 && deletedId != state.lastMessageId) return;

        ConsoleLog.warn("Counting", "Last count message deleted (or unknown). Resyncing from history...");
        logs.log("Last accepted count was deleted; global streak reset and resync triggered.");

        // Streak becomes ambiguous if a previously-valid count is manually removed.
        state.globalStreakCurrent = 0;
        store.markDirty();
        goals.markDirty();
        resyncNow(event.getJDA(), r -> {
            if (r.found()) System.out.println("Init state: last=" + r.number() + " user=" + r.userId());
            else System.out.println("Init state: no valid count found");
        });
    }

    private void accept(Parsed parsed, Message msg, String reason) {
        long now = System.currentTimeMillis();

        synchronized (store.lock) {
            CountingState state = store.state();
            state.lastNumber = parsed.number;
            state.lastUserId = parsed.authorId;
            state.userLastValidCountAt.put(parsed.authorId, now);
            state.lastMessageId = msg.getIdLong();

            state.globalStreakCurrent++;
            if (state.globalStreakCurrent > state.globalStreakBest) state.globalStreakBest = state.globalStreakCurrent;

            store.markDirty();
        }

        synchronized (stats.lock) {
            UserStats us = stats.data().getOrCreate(parsed.authorId);
            us.onCorrect(now);
            us.posCounts++;
            stats.markDirty();
        }
        achievements.onTrigger(AchievementTrigger.VALID_COUNT, msg.getGuild().getIdLong(), parsed.authorId);
        // If a goal is active and this count hits the target, award goal winner.
            if (goals.isActive() && parsed.number == goals.target()) {
            achievements.unlockById(msg.getGuild().getIdLong(), parsed.authorId, "goal_winner");
        }
        goals.markDirty();
        logDecision(reason, msg);
    }

    private void markIncorrect(Message msg) {
        long now = System.currentTimeMillis();

        synchronized (stats.lock) {
            stats.data().getOrCreate(msg.getAuthor().getIdLong()).onIncorrect(now);
            stats.markDirty();
        }

        synchronized (store.lock) {
            store.state().globalStreakCurrent = 0;
            store.markDirty();
        }
        achievements.onTrigger(AchievementTrigger.INVALID_COUNT, msg.getGuild().getIdLong(), msg.getAuthor().getIdLong());
    }

    private void delete(Message msg) {
        msg.delete().queue(
                ok -> {},
                err -> ConsoleLog.error("Counting", "Delete failed: " + err.getMessage(), err)
        );
        logs.log("Deleted invalid count by " + msg.getAuthor().getName() + ": " + msg.getContentRaw());
    }

    private void logDecision(String reason, Message msg) {
        ConsoleLog.info("Counting",
                reason
                        + " | author=" + msg.getAuthor().getId()
                        + " name=" + msg.getAuthor().getName()
                        + " content=\"" + msg.getContentRaw() + "\"");
    }

    /**
     * Strict integer parse:
     * - entire content must match -?\d+
     * - no leading zeros like 01, -01, 0002
     */
    private Parsed parseStrictCount(Message msg) {
        String s = msg.getContentRaw().trim();
        if (s.isEmpty()) return null;

        // Must be exactly integer (no spaces, no extra text)
        // Must be exactly digits (no minus, no spaces, no extra text)
        if (!s.matches("\\d+")) return null;

        // Leading zeros disallowed: "0" ok; "01", "0002" not ok
        if (s.length() > 1 && s.startsWith("0")) return null;


        try {
            long n = Long.parseLong(s);
            return new Parsed(n, msg.getAuthor().getIdLong());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public void resyncNow(JDA jda, Consumer<ResyncResult> cb) {
        TextChannel ch = jda.getTextChannelById(config.countingChannelId);

        if (ch == null) {
            cb.accept(new ResyncResult(false, -1, 0, 0));
            return;
        }

        ch.getHistory().retrievePast(10).queue(history -> {
            Parsed found = null;
            Message foundMsg = null;
            ConsoleLog.info("Resync", "Starting resync (retrievePast=10) channelId=" + config.countingChannelId);
            for (Message m : history) {
                Parsed p = parseStrictCount(m);
                if (p != null) {
                    found = p;
                    foundMsg = m;
                    ConsoleLog.info("Resync", "Found last valid: n=" + found.number + " userId=" + found.authorId + " msgId=" + foundMsg.getId());
                    break;
                }
            }

            if (found == null) {
                synchronized (store.lock) {
                    store.state().lastNumber = -1;
                    store.state().lastUserId = 0;
                    store.state().lastMessageId = 0;
                    store.markDirty();
                    ConsoleLog.warn("Resync", "No valid count found in recent history");
                    cb.accept(new ResyncResult(false, -1, 0, 0));
                    return;
                }
            }

            synchronized (store.lock) {
                store.state().lastNumber = found.number;
                store.state().lastUserId = found.authorId;
                store.state().lastMessageId = foundMsg.getIdLong();
                store.markDirty();
                goals.markDirty();
            }

            cb.accept(new ResyncResult(true, found.number, found.authorId, foundMsg.getIdLong()));
        }, err -> {
            ConsoleLog.error("Resync", "History fetch failed: " + err.getMessage(), err);
            cb.accept(new ResyncResult(false, -1, 0, 0));
        });
    }

    private record Parsed(long number, long authorId) {}
    public record ResyncResult(boolean found, long number, long userId, long messageId) {}

}
