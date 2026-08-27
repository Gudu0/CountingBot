package org.gudu0.countingbot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.gudu0.countingbot.suggestions.SuggestionEntry;
import org.gudu0.countingbot.suggestions.SuggestionsService;
import org.gudu0.countingbot.util.ConsoleLog;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class SuggestResponseListener extends ListenerAdapter {
    private final SuggestionsService suggestionsService;

    public SuggestResponseListener(SuggestionsService suggestions) {
        this.suggestionsService = suggestions;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("suggestion_response")) return;
        if (ConsoleLog.DEBUG) {
            ConsoleLog.debug("Command - " + this.getClass().getSimpleName(),
                    "/" + event.getName()
                            + (event.getSubcommandName() != null ? " " + event.getSubcommandName() : "")
                            + " by userId=" + event.getUser().getId()
                            + " name=" + event.getUser().getName()
                            + " guildId=" + (event.getGuild() != null ? event.getGuild().getId() : "DM")
                            + " channelId=" + event.getChannel().getId());
        }
        //Must Be gudu0
        if (event.getMember() == null || CommandGuards.requireUser(event, 733113260496126053L)) {
            event.reply("You don't have permission to use this.").setEphemeral(true).queue();
            return;
        }
        int suggestionID = Objects.requireNonNull(event.getOption("suggestion_number")).getAsInt();
        String response =  Objects.requireNonNull(event.getOption("response")).getAsString();
        String status = event.getOption("status") != null ? Objects.requireNonNull(event.getOption("status")).getAsString() : "Considered";

        SuggestionEntry suggestion =  suggestionsService.getSuggestion(suggestionID);
        if (suggestion == null) {
            event.reply("No suggestion found with number " + suggestionID + ".").setEphemeral(true).queue();
            return;
        }

        suggestionsService.dmUserForSuggestionResponse(suggestion.authorId, suggestion, response, status);
    }
}
