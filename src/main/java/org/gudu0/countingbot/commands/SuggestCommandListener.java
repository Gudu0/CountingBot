package org.gudu0.countingbot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.gudu0.countingbot.suggestions.SuggestionEntry;
import org.gudu0.countingbot.suggestions.SuggestionsService;
import org.gudu0.countingbot.util.ConsoleLog;

import java.util.Objects;

public class SuggestCommandListener extends ListenerAdapter {
    private final SuggestionsService suggestions;

    public SuggestCommandListener(SuggestionsService suggestions) {
        this.suggestions = suggestions;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("suggest")) return;
        if (ConsoleLog.DEBUG) {
            ConsoleLog.debug("Command - " + this.getClass().getSimpleName(),
                    "/" + event.getName()
                            + (event.getSubcommandName() != null ? " " + event.getSubcommandName() : "")
                            + " by userId=" + event.getUser().getId()
                            + " name=" + event.getUser().getName()
                            + " guildId=" + (event.getGuild() != null ? event.getGuild().getId() : "DM")
                            + " channelId=" + event.getChannel().getId());
        }

        String text = Objects.requireNonNull(event.getOption("text")).getAsString().trim();
        if (text.isEmpty()) {
            event.reply("Suggestion text can't be empty.").setEphemeral(true).queue();
            return;
        }
        if (text.length() > 1500) {
            event.reply("Keep it under 1500 characters.").setEphemeral(true).queue();
            return;
        }

        long guildId = event.getGuild() != null ? event.getGuild().getIdLong() : 0;
        long authorId = event.getUser().getIdLong();
        String authorTag = event.getUser().getName(); // snapshot

        SuggestionEntry entry = suggestions.addSuggestion(guildId, authorId, authorTag, text);

        event.reply("Submitted suggestion #" + entry.id + ". Thanks!")
                .setEphemeral(true)
                .queue();
    }
}
