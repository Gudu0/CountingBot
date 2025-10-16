const { SlashCommandBuilder, MessageFlags } = require('discord.js');

module.exports = {
	cooldown: 5,
	data: new SlashCommandBuilder().setName('ping').setDescription('Replies with Pong!'),
	async execute(interaction) {
		await interaction.deferReply({ flags: MessageFlags.Ephemeral });
		await interaction.editReply('Pong!');
	},
};