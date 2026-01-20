package org.gudu0.countingbot.disconnects;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionRecreateEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.gudu0.countingbot.config.GlobalConfig;
import org.gudu0.countingbot.util.ConsoleLog;
import org.jetbrains.annotations.NotNull;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.gudu0.countingbot.logging.LogService.TS;
import static org.gudu0.countingbot.logging.LogService.ZONE;

public class DisconnectDailyReporter extends ListenerAdapter {

    private final GlobalConfig cfg;
    private final DisconnectStore store;

    // Debounce: only count once per disconnect cycle
    private volatile boolean inDisconnect = false;

    // IMPORTANT: match bot logging zone (avoid UTC host weirdness)
    private final ZoneId zone = ZONE;

    private static final DateTimeFormatter DAY_KEY   = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DAY_TITLE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FMT  = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean creatingMessage = new AtomicBoolean(false);

    private volatile JDA jda;

    public DisconnectDailyReporter(GlobalConfig cfg, DisconnectStore store) {
        this.cfg = cfg;
        this.store = store;
    }

    @Override
    public void onReady(ReadyEvent event) {
        this.jda = event.getJDA();

        ensureTodayMessage();

        // Periodic rollover check so a new daily message appears even if no disconnects happen at midnight
        scheduler.scheduleAtFixedRate(this::ensureTodayMessageSafe, 30, 30, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));
    }

    @Override
    public void onSessionDisconnect(@NotNull SessionDisconnectEvent event) {
        if (inDisconnect) return;
        inDisconnect = true;
        recordDisconnect();
        ConsoleLog.warn("DisconnectReport", "Disconnect at " + ZonedDateTime.now(zone).format(TS));
    }

    @Override
    public void onSessionResume(@NotNull SessionResumeEvent event) {
        if (!inDisconnect) return;
        inDisconnect = false;
        recordReconnect();
        ConsoleLog.warn("DisconnectReport", "Resume at " + ZonedDateTime.now(zone).format(TS));
    }

    @Override
    public void onSessionRecreate(@NotNull SessionRecreateEvent event) {
        if (!inDisconnect) return;
        inDisconnect = false;
        recordReconnect();
    }

    private void recordDisconnect() {
        ensureTodayMessage();

        DisconnectDailyState st = store.state();
        st.disconnects++;
        st.lastDisconnectMs = System.currentTimeMillis();

        persistAndUpdateMessage();
    }

    private void recordReconnect() {
        ensureTodayMessage();

        DisconnectDailyState st = store.state();
        st.lastReconnectMs = System.currentTimeMillis();

        persistAndUpdateMessage();
    }

    private void ensureTodayMessageSafe() {
        try {
            ensureTodayMessage();
        } catch (Exception e) {
            ConsoleLog.error("DisconnectReport", "ensureTodayMessage failed: " + e.getMessage(), e);
        }
    }

    private MessageChannel resolveChannel() {
        if (jda == null) return null;
        if (cfg.disconnectThreadId == null || cfg.disconnectThreadId.isBlank()) return null;

        MessageChannel ch = jda.getChannelById(MessageChannel.class, cfg.disconnectThreadId);
        if (ch == null) {
            ConsoleLog.warn("DisconnectReport", "disconnectThreadId not found: " + cfg.disconnectThreadId);
        }
        return ch;
    }

    private void saveSafely() {
        try {
            store.save();
        } catch (Exception e) {
            ConsoleLog.error("DisconnectReport", "Failed to save disconnect state: " + e.getMessage(), e);
        }
    }

    private synchronized void ensureTodayMessage() {
        if (jda == null) return;
        if (cfg.disconnectThreadId == null || cfg.disconnectThreadId.isBlank()) return;

        String todayKey = LocalDate.now(zone).format(DAY_KEY);
        DisconnectDailyState st = store.state();

        // If date changed, reset daily counters and create a new message
        if (st.date == null || !st.date.equals(todayKey)) {
            st.date = todayKey;
            st.disconnects = 0;
            st.lastDisconnectMs = 0;
            st.lastReconnectMs = 0;
            st.messageId = 0;
            creatingMessage.set(false);
            saveSafely(); // persist the rollover even if Discord send fails
        }

        if (st.messageId != 0) return;

        // Prevent duplicate sends while the async send is still pending
        if (!creatingMessage.compareAndSet(false, true)) return;

        MessageChannel ch = resolveChannel();
        if (ch == null) {
            creatingMessage.set(false);
            return;
        }

        String content = buildMessage(st);
        ch.sendMessage(content).queue(
                msg -> {
                    st.messageId = msg.getIdLong();
                    creatingMessage.set(false);
                    saveSafely();
                },
                err -> {
                    creatingMessage.set(false);
                    ConsoleLog.error("DisconnectReport", "Failed to create daily disconnect message: " + err.getMessage(), err);
                }
        );
    }

    private void persistAndUpdateMessage() {
        DisconnectDailyState st = store.state();
        saveSafely();

        MessageChannel ch = resolveChannel();
        if (ch == null) return;

        if (st.messageId == 0) {
            ensureTodayMessage();
            return;
        }

        ch.editMessageById(st.messageId, buildMessage(st)).queue(
                ok -> {},
                err -> {
                    // If message was deleted, recreate it (like GoalsService behavior)
                    if (err instanceof ErrorResponseException e && e.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                        ConsoleLog.warn("DisconnectReport", "Daily message was deleted; recreating.");
                        st.messageId = 0;
                        saveSafely();
                        ensureTodayMessage();
                        return;
                    }
                    ConsoleLog.error("DisconnectReport", "Failed to edit disconnect message: " + err.getMessage(), err);
                }
        );
    }

    private String buildMessage(DisconnectDailyState st) {
        String day = LocalDate.parse(st.date, DAY_KEY).format(DAY_TITLE);

        String lastDisc = st.lastDisconnectMs == 0 ? "none" :
                Instant.ofEpochMilli(st.lastDisconnectMs).atZone(zone).format(TIME_FMT);

        String lastRe = st.lastReconnectMs == 0 ? "none" :
                Instant.ofEpochMilli(st.lastReconnectMs).atZone(zone).format(TIME_FMT);

        return "**Gateway disconnects for " + day + "**\n"
                + "Disconnects: **" + st.disconnects + "**\n"
                + "Last disconnect: **" + lastDisc + "**\n"
                + "Last reconnect: **" + lastRe + "**";
    }
}
