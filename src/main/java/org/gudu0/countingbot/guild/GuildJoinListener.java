package org.gudu0.countingbot.guild;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.gudu0.countingbot.SafetyChecks;
import org.gudu0.countingbot.util.ConsoleLog;

import java.util.function.BiConsumer;

/**
 * Detects when the bot is added to a new server while running.
 * - Creates/loads GuildContext (data/guilds/<id>/...)
 * - Runs safety checks (sets enforceDelete properly)
 * - Registers guild commands for that guild
 */
public class GuildJoinListener extends ListenerAdapter {

    private final GuildManager guilds;

    /**
     * Callback that registers commands for exactly one guild.
     * Signature: (JDA, Guild) -> void
     */
    private final BiConsumer<JDA, Guild> registerCommandsForGuild;

    public GuildJoinListener(GuildManager guilds, BiConsumer<JDA, Guild> registerCommandsForGuild) {
        this.guilds = guilds;
        this.registerCommandsForGuild = registerCommandsForGuild;
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        Guild g = event.getGuild();
        long guildId = g.getIdLong();

        ConsoleLog.warn("GuildJoin", "Joined new guild: " + g.getName() + " (" + guildId + ")");

        // Ensure folder/config/stores exist
        GuildContext ctx = guilds.get(guildId);

        // Re-run safety checks (will disable enforceDelete if not configured/perms missing)
        SafetyChecks.runForGuild(event.getJDA(), ctx);

        // Register slash commands in this new guild
        registerCommandsForGuild.accept(event.getJDA(), g);

        ConsoleLog.info("GuildJoin", "Handled join for guildId=" + guildId);
    }
}
