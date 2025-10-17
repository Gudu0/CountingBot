const { SlashCommandBuilder, MessageFlags } = require('discord.js');
const counting = require('../../counting.js');

module.exports = {
    data: new SlashCommandBuilder()
        .setName('graph')
        .setDescription('Shows a bar graph of daily counting activity!'),
    async execute(interaction) {
        await interaction.deferReply({ flags: MessageFlags.Ephemeral });

        // Get last 7 days
        const days = [...counting.dailyCounts.keys()].sort().slice(-7);
        const bars = days.map(day => {
            const count = counting.dailyCounts.get(day);
            const bar = '█'.repeat(Math.min(count, 20)); // Max 20 bars for display
            return `${day}: ${bar} (${count})`;
        }).join('\n') || 'No data!';

        await interaction.editReply(`**Counting Activity (last 7 days):**\n${bars}\n` + 'I don\'t know what this will look like, but hopefully it works!');
    },
};