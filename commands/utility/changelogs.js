const { SlashCommandBuilder, ActionRowBuilder, StringSelectMenuBuilder, PermissionFlagsBits, ButtonBuilder, ButtonStyle, MessageFlags } = require('discord.js');
const fs = require('node:fs');
const path = require('node:path');

module.exports = {
    data: new SlashCommandBuilder()
        .setName('setloglines')
        .setDescription('Choose which individual log lines go to the log channel')
        .setDefaultMemberPermissions(PermissionFlagsBits.BanMembers),
    async execute(interaction) {
        try {
            const settingsPath = path.join(__dirname, '../../logSettings.json');
            let settings = {};
            if (fs.existsSync(settingsPath)) {
                settings = JSON.parse(fs.readFileSync(settingsPath, 'utf8'));
            }
            const guildId = interaction.guildId;
            // Get all log keys for this guild (or use a default set if not present)
            const logKeys = settings[guildId]
                ? Object.keys(settings[guildId].logs)
                : [
                    "gateway_connection_closed", "gateway_connection_ready", "gateway_connection_resumed",
                    "command_missing_data_or_execute", "logging_initialized", "error_logging_initialized",
                    "log_thread_not_found", "counting_channel_not_found", "last_user_initialized",
                    "last_number_initialized", "error_fetching_last_message", "non_numeric", "counted_too_fast",
                    "last_number_not_loaded", "correct_count", "incorrect_number", "message_not_found",
                    "counted_twice_in_a_row", "incorrect_count", "saving_stats", "error_saving_stats",
                    "no_stats_file", "checked_countstats", "checked_leaderboard", "deleting_message",
                    "bot_started", "log_channel_not_found", "gateway_error"
                ];

            // Build select menu options
            const logLineOptions = logKeys.map(key => ({
                label: key.replace(/_/g, ' '),
                value: key,
                default: settings[guildId]?.logs[key] ?? true
            }));

            // Split options into groups of 25
            const rows = [];
            for (let i = 0; i < logLineOptions.length; i += 25) {
                const menu = new StringSelectMenuBuilder()
                    .setCustomId(`loglines_select_${i / 25}`)
                    .setPlaceholder('Select log lines...')
                    .setMinValues(0)
                    .setMaxValues(Math.min(25, logLineOptions.length - i))
                    .addOptions(logLineOptions.slice(i, i + 25));
                rows.push(new ActionRowBuilder().addComponents(menu));
            }
            const submitButton = new ButtonBuilder()
                .setCustomId('loglines_submit')
                .setLabel('Submit')
                .setStyle(ButtonStyle.Primary);
            
            rows.push(new ActionRowBuilder().addComponents(submitButton));

            await interaction.reply({
                content: 'Select which log lines you want to send to the log channel:',
                components: rows,
                flags: MessageFlags.Ephemeral
            });
        } catch (err) {
            console.error('Error executing setloglines command:', err);
            await interaction.reply({
                content: `❌ An error occurred while executing the command: ${err.message}`,
                flags: MessageFlags.Ephemeral
            });
        }
    }
}