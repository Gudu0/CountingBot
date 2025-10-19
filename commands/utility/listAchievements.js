const { SlashCommandBuilder, MessageFlags } = require('discord.js');
const definitions = require('../../data/achievementDefinitions.json');

module.exports = {
    cooldown: 5,
    data: new SlashCommandBuilder()
        .setName('achievementlist')
        .setDescription('View all possible achievements for CountingBot!'),
    async execute(interaction) {
        await interaction.deferReply({ flags: MessageFlags.Ephemeral });

        let output = '**Achievements:**\n';
        for (const [id, { name, description }] of Object.entries(definitions)) {
            output += `• **${name}** — ${description}\n`;
        }

        await interaction.editReply({ content: output });
    },
};