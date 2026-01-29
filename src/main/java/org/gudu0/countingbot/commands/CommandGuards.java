package org.gudu0.countingbot.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.EnumSet;
import java.util.stream.Collectors;

public interface CommandGuards {

    // --- Guild / member guards ---

    default Guild requireGuild(SlashCommandInteractionEvent event) {
        Guild g = event.getGuild();
        if (g == null) {
            event.reply("This command can only be used in a server.")
                    .setEphemeral(true).queue();
            return null;
        }
        return g;
    }

    default boolean requireMemberPerms(SlashCommandInteractionEvent event, Permission... perms) {
        Member m = event.getMember();
        if (m == null || !m.hasPermission(perms)) {
            event.reply("You don't have permission to use this.")
                    .setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    // Convenience: “admin” like you already do in /setup
    default boolean requireAdmin(SlashCommandInteractionEvent event) {
        return requireMemberAnyPerm(event, Permission.MANAGE_SERVER, Permission.BAN_MEMBERS);
    }

    default boolean requireMemberAnyPerm(SlashCommandInteractionEvent event, Permission... anyOf) {
        Member m = event.getMember();
        if (m == null) {
            event.reply("You don't have permission to use this.")
                    .setEphemeral(true).queue();
            return false;
        }
        for (Permission p : anyOf) {
            if (m.hasPermission(p)) return true;
        }
        event.reply("You don't have permission to use this.")
                .setEphemeral(true).queue();
        return false;
    }

    // --- Bot permission guards (channel-specific) ---

    default boolean requireBotPerms(
            SlashCommandInteractionEvent event,
            GuildChannel channel,
            Permission... perms
    ) {
        Guild g = event.getGuild();
        if (g == null) {
            event.reply("This command can only be used in a server.")
                    .setEphemeral(true).queue();
            return false;
        }

        Member self = g.getSelfMember();

        EnumSet<Permission> missing = EnumSet.noneOf(Permission.class);
        for (Permission p : perms) {
            if (!self.hasPermission(channel, p)) missing.add(p);
        }

        if (missing.isEmpty()) return true;

        String missingStr = missing.stream()
                .map(Permission::getName)
                .collect(Collectors.joining(", "));

        event.reply("I’m missing permissions in <#" + channel.getId() + ">: **" + missingStr + "**")
                .setEphemeral(true).queue();
        return false;
    }
}
