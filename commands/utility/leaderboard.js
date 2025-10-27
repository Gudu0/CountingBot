const { SlashCommandBuilder, MessageFlags } = require('discord.js');
const counting = require('../../counting.js');
const logger = require('../../logger.js');
const ENV = require("../../data/config.json");
let sqlite;
try {
    sqlite = require('../../db');
} catch (_) {
    sqlite = null;
}

module.exports = {
	cooldown: 5,
	data: new SlashCommandBuilder().setName('leaderboard').setDescription('Replies with the leaderboard!'),
	async execute(interaction) {
        await interaction.deferReply({ flags: MessageFlags.Ephemeral });
        
    let fameBoard, shameBoard;
    let source = 'maps';

        if (sqlite && sqlite.db) {
            // Read from SQLite users table
            try {
                const topFame = sqlite.db.prepare('SELECT user_id, fame FROM users ORDER BY fame DESC, updated_at DESC LIMIT 5').all();
                const topShame = sqlite.db.prepare('SELECT user_id, shame FROM users ORDER BY shame DESC, updated_at DESC LIMIT 5').all();

                fameBoard = (topFame || []).map((r, i) => `${i + 1}. <@${r.user_id}>: ${r.fame} correct`).join('\n') || 'No fame data!';
                shameBoard = (topShame || []).map((r, i) => `${i + 1}. <@${r.user_id}>: ${r.shame} incorrect`).join('\n') || 'No shame data!';
                source = 'sqlite';
            } catch (e) {
                // Fallback to in-memory maps on any DB error
                const fameEntries = [...counting.mapOfFame.entries()].sort((a, b) => b[1] - a[1]);
                const shameEntries = [...counting.mapOfShame.entries()].sort((a, b) => b[1] - a[1]);
                fameBoard = fameEntries.slice(0, 5).map(([id, score], i) => `${i + 1}. <@${id}>: ${score} correct`).join('\n') || 'No fame data!';
                shameBoard = shameEntries.slice(0, 5).map(([id, score], i) => `${i + 1}. <@${id}>: ${score} incorrect`).join('\n') || 'No shame data!';
                source = 'maps';
            }
        } else {
            // Immediate fallback if DB module not available
            const fameEntries = [...counting.mapOfFame.entries()].sort((a, b) => b[1] - a[1]);
            const shameEntries = [...counting.mapOfShame.entries()].sort((a, b) => b[1] - a[1]);
            fameBoard = fameEntries.slice(0, 5).map(([id, score], i) => `${i + 1}. <@${id}>: ${score} correct`).join('\n') || 'No fame data!';
            shameBoard = shameEntries.slice(0, 5).map(([id, score], i) => `${i + 1}. <@${id}>: ${score} incorrect`).join('\n') || 'No shame data!';
            source = 'maps';
        }

        const reply = `**Fame Leaderboard:**\n${fameBoard}\n\n**Shame Leaderboard:**\n${shameBoard}`;
        await interaction.editReply(reply);
        logger.log(`Leaderboard checked (source=${source})`, 'checked_leaderboard', ENV.bs_server_id);
	},
};