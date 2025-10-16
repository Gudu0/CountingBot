const { SlashCommandBuilder, MessageFlags } = require('discord.js');

module.exports = {
	data: new SlashCommandBuilder().setName('server').setDescription('Provides information about the server.'),
	async execute(interaction) {
		await interaction.deferReply({ flags: MessageFlags.Ephemeral });
		// interaction.guild is the object representing the Guild in which the command was run
		await interaction.editReply(
			`This server is ${interaction.guild.name} and has ${interaction.guild.memberCount} members.`,
		);
	},
};