const { SlashCommandBuilder, MessageFlags } = require('discord.js');

module.exports = {
	cooldown: 5,
	testonly: true,
	data: new SlashCommandBuilder()
	.setName('deletelastmessage')
	.setDescription('Deletes the last message in the channel.')
	.addStringOption(option =>
		option.setName('messageid')
			.setDescription('The ID of the message to delete')
			.setRequired(true))
			.addStringOption(option =>
		option.setName('channelid')
			.setDescription('The channel ID')
			.setRequired(true)),
	async execute(interaction) {
		const messageId = interaction.options.getString('messageid');
		const channelId = interaction.options.getString('channelid');
		const channel = interaction.client.channels.cache.get(channelId);

		if (!channel) {
				await interaction.reply({ content: 'Channel not found!', flags: MessageFlags.Ephemeral });
				return;
		}

		try {
			const message = await channel.messages.fetch(messageId);
			await message.delete();
			await interaction.reply({ content: 'Message deleted!', flags: MessageFlags.Ephemeral });
		} catch (err) {
			await interaction.reply({ content: `Error: ${err.message}`, flags: MessageFlags.Ephemeral });
		}
		},
};