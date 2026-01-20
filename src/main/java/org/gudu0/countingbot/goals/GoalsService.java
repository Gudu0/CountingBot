package org.gudu0.countingbot.goals;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.gudu0.countingbot.counting.StateStore;
import org.gudu0.countingbot.config.GuildConfig;
import org.gudu0.countingbot.util.ConsoleLog;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("unused")
public class GoalsService {
    private final GuildConfig cfg;
    private final GoalsStore goalsStore;
    private final StateStore stateStore;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean dirty = new AtomicBoolean(true); // true so we render once at boot

    private volatile JDA jda;

    // Throttle: only edit at most once every N seconds (when dirty).
    @SuppressWarnings("FieldCanBeLocal")
    private final long updatePeriodSeconds = 15;

    public GoalsService(GuildConfig cfg, GoalsStore goalsStore, StateStore stateStore) {
        this.cfg = cfg;
        this.goalsStore = goalsStore;
        this.stateStore = stateStore;
    }

    public void attach(JDA jda) {
        this.jda = jda;
        ensureMessageExistsAndRender(true);

        ConsoleLog.info("Goals - attach","Starting scheduler");
        scheduler.scheduleAtFixedRate(() -> {
            if (!dirty.get()) return;
            ensureMessageExistsAndRender(false);
        }, updatePeriodSeconds, updatePeriodSeconds, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));
    }

    /** Call this when progress *might* have changed (valid count, resync, etc.) */
    public void markDirty() {
        dirty.set(true);
    }

    /** Updates goal definition and forces an immediate render. */
    public void setGoal(long target, long setByUserId, Long deadlineAtMillis) {
        GoalState gs = goalsStore.state();
        gs.active = true;
        gs.target = target;
        gs.setByUserId = setByUserId;
        gs.setAtMillis = System.currentTimeMillis();
        gs.deadlineAtMillis = deadlineAtMillis;

        // Force re-render even if count didn't change.
        gs.lastRenderedNumber = Long.MIN_VALUE;

        goalsStore.markDirty();
        markDirty();
        ensureMessageExistsAndRender(true);
    }

    public void clearGoal() {
        GoalState gs = goalsStore.state();
        gs.active = false;
        gs.target = 0;
        gs.setByUserId = 0;
        gs.setAtMillis = System.currentTimeMillis();
        gs.deadlineAtMillis = null;
        gs.lastRenderedNumber = Long.MIN_VALUE;

        goalsStore.markDirty();
        markDirty();
        ensureMessageExistsAndRender(true);
    }

    private void ensureMessageExistsAndRender(boolean immediate) {
        JDA j = this.jda;
        if (j == null) return;

        TextChannel ch = j.getTextChannelById(cfg.countingChannelId);
        if (ch == null) return;

        GoalState gs = goalsStore.state();

        // Ensure message exists (or re-create if deleted)
        if (gs.goalMessageId != 0) {
            ch.retrieveMessageById(gs.goalMessageId).queue(
                    msg -> renderTo(msg, gs),
                    err -> {
                        // Message was likely deleted. Recreate.
                        gs.goalMessageId = 0;
                        goalsStore.markDirty();
                        createMessage(ch, gs);
                    }
            );
            return;
        }

        createMessage(ch, gs);
    }

    private void createMessage(TextChannel ch, GoalState gs) {

        ch.sendMessageEmbeds(buildEmbed().build()).queue(
                msg -> {
                    gs.goalMessageId = msg.getIdLong();
                    goalsStore.markDirty();
                    // Try pin (optional; ignore failures)
                    msg.pin().queue(ok -> {}, err -> ConsoleLog.warn("Goals", "Goal pinning failed."));
                    renderTo(msg, gs); // render actual contents right away
                    ConsoleLog.info("Goals", "Created goal message id=" + msg.getId());
                },
                err -> ConsoleLog.error("Goals", "Failed to create goal message")
        );
    }

    private void renderTo(Message msg, GoalState gs) {
        long lastNumber = stateStore.state().lastNumber;

        // De-dupe: don’t edit if nothing meaningful changed.
        if (gs.lastRenderedNumber == lastNumber && !dirty.get()) return;

        // If active goal, but bad target, treat as inactive.
        if (gs.active && gs.target <= 0) gs.active = false;

        msg.editMessageEmbeds(buildEmbed().build()).queue(
                ok -> {
                    gs.lastRenderedNumber = lastNumber;
                    goalsStore.markDirty();
                    dirty.set(false);
                    ConsoleLog.debug("Goals", "Edited goal message id=" + msg.getId() + " lastNumber=" + lastNumber);
                },
                err -> ConsoleLog.error("Goals","Failed to edit goals message.")
        );
    }

    private EmbedBuilder buildEmbed() {
        GoalState gs = goalsStore.state();
        long lastNumber = stateStore.state().lastNumber;
        long nowMs = System.currentTimeMillis();

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Counting Goal");
        eb.setColor(0x2ecc71); // green stripe like your screenshot vibe

        if (!gs.active) {
            eb.setDescription("No active goal");
            eb.addField("Progress", progressBlock(-1, -1, nowMs), false);

            return eb;
        }

        eb.setDescription("Reach " + gs.target);

        eb.addField("Set by", gs.setByUserId != 0 ? "<@" + gs.setByUserId + ">" : "Unknown", true);
        eb.addField("Target", String.valueOf(gs.target), true);

        String deadlineStr = "None";
        if (gs.deadlineAtMillis != null) {
            deadlineStr = "<t:" + (gs.deadlineAtMillis / 1000) + ":R>";
        }
        eb.addField("Deadline", deadlineStr, true);

        long clampedLast = Math.max(0, lastNumber);
        eb.addField("Progress", progressBlock(clampedLast, gs.target, nowMs), false);


        return eb;
    }

    private static String progressBlock(long value, long target, long nowMs) {
        if (target <= 0) {
            return "`" + "░".repeat(22) + "` 0%\n0 / 0\n<t:" + (nowMs / 1000) + ":F>";
        }

        double pct = (double) value / (double) target;
        if (pct < 0) pct = 0;
        if (pct > 1) pct = 1;

        int percent = (int) Math.floor(pct * 100.0);

        int width = 22; // tweak if you want longer/shorter
        int filled = (int) Math.round(pct * width);
        if (filled < 0) filled = 0;
        if (filled > width) filled = width;

        String bar = "`" + "█".repeat(filled) + "░".repeat(width - filled) + "`";

        return bar + " " + percent + "%\n"
                + value + " / " + target + "\n"
                + "<t:" + (nowMs / 1000) + ":F>";
    }

    public MessageEmbed buildGoalEmbed() {
        return buildEmbed().build();
    }

    public boolean isActive(){
        return goalsStore.state().active;
    }

    public long target(){
        return goalsStore.state().target;
    }

}
