package org.gudu0.countingbot.suggestions;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.gudu0.countingbot.config.BotConfig;
import org.gudu0.countingbot.util.ConsoleLog;

import java.time.Instant;

public class SuggestionsService {
    private final BotConfig cfg;
    private final SuggestionsStore store;

    private volatile JDA jda;

    public SuggestionsService(BotConfig cfg, SuggestionsStore store) {
        this.cfg = cfg;
        this.store = store;
    }

    public void attach(JDA jda) {
        this.jda = jda;
    }

    public SuggestionEntry addSuggestion(long guildId, long authorId, String authorTag, String text) {
        long now = System.currentTimeMillis();
        SuggestionEntry entry;

        synchronized (store.lock) {
            long id = store.state().nextId++;
            entry = new SuggestionEntry(id, guildId, authorId, authorTag, text, now);
            store.state().suggestions.add(entry);
            store.markDirty();
        }

        ConsoleLog.info("Suggestions", "Add id=" + entry.id + " authorId=" + authorId + " len=" + text.length());

        postToSuggestionsChannel(entry);
        // optional: DM notify
        dmOwner(entry);

        return entry;
    }

    private void postToSuggestionsChannel(SuggestionEntry e) {
        JDA j = this.jda;
        if (j == null) return;

        long threadId = parseId(cfg.suggestionsThreadId);
        if (threadId == 0) return;

        MessageChannel ch = j.getChannelById(MessageChannel.class, threadId);
        if (ch == null) return;

        String msg =
                "**Suggestion #" + e.id + "**\n" +
                        "From: <@" + e.authorId + ">" + (e.authorTag != null ? " (" + e.authorTag + ")" : "") + "\n" +
                        "When: <t:" + (e.createdAtMillis / 1000) + ":F>\n" +
                        e.text;

        ch.sendMessage(msg).queue(
                ok -> {},
                err -> System.err.println("Failed to post suggestion to thread/channel: " + err.getMessage())
        );
    }

    private void dmOwner(SuggestionEntry e) {
        JDA j = this.jda;
        if (j == null) return;

        long ownerId = parseId(cfg.suggestionsNotifyUserId);
        if (ownerId == 0) return;

        j.retrieveUserById(ownerId).queue(
                user -> sendDm(user, e),
                err -> System.err.println("Failed to retrieve notify user: " + err.getMessage())
        );
    }

    private void sendDm(User user, SuggestionEntry e) {
        String msg =
                "New suggestion #" + e.id + "\n" +
                        "From: <@" + e.authorId + ">" + (e.authorTag != null ? " (" + e.authorTag + ")" : "") + "\n" +
                        "When: " + Instant.ofEpochMilli(e.createdAtMillis) + "\n" +
                        "Suggestion:\n\n" + e.text;

        user.openPrivateChannel().queue(
                pc -> pc.sendMessage(msg).queue(
                        ok -> {},
                        err -> System.err.println("DM send failed (privacy/settings): " + err.getMessage())
                ),
                err -> System.err.println("Open DM failed: " + err.getMessage())
        );
    }

    private static long parseId(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.isEmpty()) return 0;
        try { return Long.parseLong(s); }
        catch (NumberFormatException e) { return 0; }
    }
}
