package org.gudu0.countingbot.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.gudu0.countingbot.goals.GuildGoalsServiceRegistry;
import org.gudu0.countingbot.goals.GoalsService;
import org.gudu0.countingbot.guild.GuildManager;
import org.gudu0.countingbot.util.ConsoleLog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * /goal set|clear|view
 *
 * Multi-guild: routes to GoalsService for the calling guild (data/guilds/<guildId>/goals.json).
 */
public class GoalCommandListener extends ListenerAdapter {

    private final GuildManager guilds;
    private final GuildGoalsServiceRegistry goalsRegistry;

    public GoalCommandListener(GuildManager guilds, GuildGoalsServiceRegistry goalsRegistry) {
        this.guilds = guilds;
        this.goalsRegistry = goalsRegistry;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("goal")) return;

        ConsoleLog.info("Command", "Goal Command Used");
        if (ConsoleLog.DEBUG) {
            ConsoleLog.debug("Command - " + this.getClass().getSimpleName(),
                    "/" + event.getName()
                            + (event.getSubcommandName() != null ? " " + event.getSubcommandName() : "")
                            + " by userId=" + event.getUser().getId()
                            + " name=" + event.getUser().getName()
                            + " guildId=" + (event.getGuild() != null ? event.getGuild().getId() : "DM")
                            + " channelId=" + event.getChannel().getId());
        }

        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();
        // Ensure the context exists (loads data/guilds/<guildId>/config.json etc.)
        guilds.get(guildId);

        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("Missing subcommand. Use /goal set|clear|view").setEphemeral(true).queue();
            return;
        }

        boolean isAdmin = event.getMember() != null && event.getMember().hasPermission(Permission.BAN_MEMBERS);

        if ((sub.equals("set") || sub.equals("clear")) && !isAdmin) {
            event.reply("You don't have permission to do that.").setEphemeral(true).queue();
            ConsoleLog.warn("Command", "Denied (missing permission) userId=" + event.getUser().getId());
            return;
        }

        GoalsService goals = goalsRegistry.getOrCreate(guildId);

        switch (sub) {
            case "set" -> {
                long target = Objects.requireNonNull(event.getOption("target")).getAsLong();
                if (target <= 0) {
                    event.reply("Target must be > 0.").setEphemeral(true).queue();
                    return;
                }

                String deadlineRaw = event.getOption("deadline") != null
                        ? Objects.requireNonNull(event.getOption("deadline")).getAsString()
                        : null;
                Long deadlineMs = parseDeadline(deadlineRaw);

                goals.setGoal(target, event.getUser().getIdLong(), deadlineMs);
                event.reply("Goal set: Reach " + target
                                + (deadlineMs != null ? " (deadline <t:" + (deadlineMs / 1000) + ":R>)" : "")
                                + ".")
                        .setEphemeral(true)
                        .queue();

                if (ConsoleLog.DEBUG) {
                    ConsoleLog.debug("Goal - Set", "guildId=" + guildId + " target=" + target + " deadlineMs=" + deadlineMs);
                }
            }
            case "clear" -> {
                goals.clearGoal();
                event.reply("Goal cleared.").setEphemeral(true).queue();
                ConsoleLog.warn("Goal - Clear", "guildId=" + guildId + " cleared goal");
            }
            case "view" -> event.replyEmbeds(goals.buildGoalEmbed()).setEphemeral(true).queue();
            default -> event.reply("Unknown subcommand: " + sub).setEphemeral(true).queue();
        }
    }

    /**
     * Supported deadline formats (string option):
     * - null/empty/"none" => no deadline
     * - "in 7d", "in 12h", "in 30m", "in 45s", "in 2w"
     * - "YYYY-MM-DD" (assumes 23:59 local time)
     */
    private static Long parseDeadline(String deadline) {
        if (deadline == null) return null;
        deadline = deadline.trim();
        if (deadline.isEmpty()) return null;
        if (deadline.equalsIgnoreCase("none")) return null;

        long now = System.currentTimeMillis();

        String lower = deadline.toLowerCase();
        if (lower.startsWith("in ")) {
            String rest = lower.substring(3).trim(); // e.g. 7d
            if (rest.length() < 2) return null;

            char unit = rest.charAt(rest.length() - 1);
            String numStr = rest.substring(0, rest.length() - 1).trim();

            long n;
            try { n = Long.parseLong(numStr); }
            catch (NumberFormatException e) { return null; }

            long deltaMs = switch (unit) {
                case 's' -> n * 1000L;
                case 'm' -> n * 60_000L;
                case 'h' -> n * 3_600_000L;
                case 'd' -> n * 86_400_000L;
                case 'w' -> n * 604_800_000L;
                default -> 0L;
            };

            if (deltaMs <= 0) return null;
            return now + deltaMs;
        }

        // YYYY-MM-DD
        try {
            LocalDate d = LocalDate.parse(deadline, DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDateTime dt = d.atTime(23, 59);
            return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception ignored) {}

        return null;
    }
}
