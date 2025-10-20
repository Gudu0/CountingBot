const { SlashCommandBuilder, MessageFlags, AttachmentBuilder } = require('discord.js');
const makeProfile = require('../../make-profile.js');

module.exports = {
	data: new SlashCommandBuilder()
    .setName('profile')
    .setDescription('Replies with a generated profile!')
    .addUserOption(option =>
        option
            .setName('user')
            .setDescription('The user to get profile for')
            .setRequired(false)),

	async execute(interaction) {
        await interaction.deferReply({ flags: MessageFlags.Ephemeral });
        const user = interaction.options.getUser('user') || interaction.user;
        const userId = user.id;
        const username = user.username;
        const avatarUrl = user.displayAvatarURL({ extension: 'png', size: 128 });

        const buffer = await makeProfile.makeProfile(userId, username, avatarUrl);
        const attachment = new AttachmentBuilder(buffer, { name: 'profile.png' });

        await interaction.editReply({ content: '', files: [attachment] });
	},
};