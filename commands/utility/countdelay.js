const { SlashCommandBuilder, MessageFlags, PermissionFlagsBits } = require('discord.js');
const ENV = require('../../data/config.json');
const fs = require('fs');
const path = require('node:path');

module.exports = {
	cooldown: 5,
	data: new SlashCommandBuilder()
    .setName('countdelay')
    .setDescription('Change the delay between counts.')
    .setDefaultMemberPermissions(PermissionFlagsBits.BanMembers)
    .addIntegerOption(option =>
      option.setName('delay')
        .setDescription('The delay in milliseconds between counts.')
        .setRequired(true)),

	async execute(interaction) {
    await interaction.deferReply({ flags: MessageFlags.Ephemeral });

        const delay = interaction.options.getInteger('delay');

        ENV.counting_delay = delay;

        fs.writeFileSync(path.join(__dirname, '../../data/config.json'), JSON.stringify(ENV, null, 2));
        

		await interaction.editReply('Delay changed!');
	},
};