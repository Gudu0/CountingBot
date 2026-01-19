package org.gudu0.countingbot.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.gudu0.countingbot.config.BotConfig;
import org.gudu0.countingbot.config.ConfigStore;
import org.gudu0.countingbot.util.ConsoleLog;

import java.util.Objects;

public class CountDelayListener extends ListenerAdapter {
    private final BotConfig cfg;
    private final ConfigStore configStore;

    public CountDelayListener(BotConfig cfg, ConfigStore configStore) {
        this.cfg = cfg;
        this.configStore = configStore;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("countdelay")) return;

        ConsoleLog.info("Command - " + this.getClass().getSimpleName(),
                "/" + event.getName()
                        + (event.getSubcommandName() != null ? " " + event.getSubcommandName() : "")
                        + " by userId=" + event.getUser().getId()
                        + " name=" + event.getUser().getName()
                        + " guildId=" + (event.getGuild() != null ? event.getGuild().getId() : "DM")
                        + " channelId=" + event.getChannel().getId());


        if (event.getMember() == null || !event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You don't have permission to use this command.")
                    .setEphemeral(true).queue();
            ConsoleLog.warn("Command", "Denied (missing permission) userId=" + event.getUser().getId());
            return;
        }

        long seconds = Objects.requireNonNull(event.getOption("seconds")).getAsLong();
        if (seconds < 0 || seconds > 3600) {
            event.reply("Delay must be between 0 and 3600 seconds.")
                    .setEphemeral(true).queue();
            return;
        }

        cfg.countingDelaySeconds = (int) seconds;

        try {
            configStore.save();
        } catch (Exception e) {
            event.reply("Updated delay in memory, but failed to save config.json: " + e.getMessage())
                    .setEphemeral(true).queue();
            return;
        }

        event.reply("Set counting delay to **" + seconds + "** seconds.")
                .setEphemeral(true).queue();
    }
}
