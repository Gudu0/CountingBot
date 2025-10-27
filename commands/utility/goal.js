const { SlashCommandBuilder, MessageFlags, EmbedBuilder } = require('discord.js');
const fs = require('fs');
const path = require('path');

const goalsPath = path.join(__dirname, '../../data/goals.json');
const statsPath = path.join(__dirname, '../../data/countingStats.json');
const counting = require('../../counting.js');
let sqlite;
try { sqlite = require('../../db'); } catch { sqlite = null; }

function loadGoals() {
  if (!fs.existsSync(goalsPath)) return { current: null };
  try { return JSON.parse(fs.readFileSync(goalsPath, 'utf8')); } catch (e) { return { current: null }; }
}

function loadStats() {
  if (!fs.existsSync(statsPath)) return {};
  try { return JSON.parse(fs.readFileSync(statsPath, 'utf8')); } catch (e) { return {}; }
}

function loadGoalFromSQL() {
  try {
    if (!sqlite || !sqlite.db) return null;
    // Current goal: most recent row with no completed_at
    const row = sqlite.db.prepare(`
      SELECT id, text, target, pinned_message_id, created_at, completed_at, set_by, deadline, last_reported_percent, completed_by
      FROM goals
      WHERE completed_at IS NULL
      ORDER BY created_at DESC
      LIMIT 1
    `).get();
    if (!row) return null;
    return {
      id: row.id,
      text: row.text,
      target: row.target,
      setBy: row.set_by,
      deadline: row.deadline,
      createdAt: row.created_at,
      completedAt: row.completed_at,
      pinned_message_id: row.pinned_message_id,
      lastReportedPercent: row.last_reported_percent,
      completedBy: row.completed_by
    };
  } catch {
    return null;
  }
}

function makeProgressBar(current, target, size = 12) {
  const t = Number(target);
  if (isNaN(t) || t === 0) return { bar: '▯'.repeat(size), percent: 0 };
  let percent = 0;
  if (t > 0) percent = Math.round((current / t) * 100);
  else { // negative target
    const denom = (0 - t);
    const numer = (0 - current);
    percent = denom === 0 ? 0 : Math.round((numer / denom) * 100);
  }
  if (percent < 0) percent = 0;
  if (percent > 100) percent = 100;
  const fill = Math.round((percent / 100) * size);
  const bar = '█'.repeat(fill) + '░'.repeat(size - fill);
  return { bar, percent };
}

module.exports = {
  cooldown: 3,
  data: new SlashCommandBuilder()
    .setName('goal')
    .setDescription('View the current counting goal'),

  async execute(interaction) {
    await interaction.deferReply({ flags: MessageFlags.Ephemeral });
    // Prefer SQL source of truth; fallback to JSON file if needed
    let goal = loadGoalFromSQL();
    if (!goal) {
      const goals = loadGoals();
      goal = goals.current;
    }
    if (!goal) {
      return interaction.editReply({ content: 'There is no active counting goal.' });
    }

    // format deadline for display as Discord relative timestamp when possible
    let displayDeadline = 'None';
    let deadlineDate = null;
    if (goal.deadline) {
      if (/^\d+$/.test(String(goal.deadline))) {
        const n = Number(goal.deadline);
        deadlineDate = String(goal.deadline).length <= 10 ? new Date(n * 1000) : new Date(n);
      } else {
        const parsed = Date.parse(goal.deadline);
        if (!isNaN(parsed)) deadlineDate = new Date(parsed);
      }
      if (deadlineDate && !isNaN(deadlineDate)) {
        displayDeadline = `<t:${Math.floor(deadlineDate.getTime() / 1000)}:R>`;
      } else {
        displayDeadline = String(goal.deadline);
      }
    }

    const embed = new EmbedBuilder()
      .setTitle('Current Counting Goal')
      .setDescription(goal.text || 'No description')
      .setColor(0x00AE86)
      .addFields(
        { name: 'Set by', value: `<@${goal.setBy}>`, inline: true },
        { name: 'Target', value: goal.target !== undefined && goal.target !== null ? String(goal.target) : 'None', inline: true },
        { name: 'Deadline', value: displayDeadline, inline: true }
      )
  .setTimestamp(goal.createdAt ? new Date(goal.createdAt) : new Date());

  if (goal.target !== undefined && goal.target !== null) {
      // Use the current last number in the counting channel as the progress metric
      let currentProgress = counting && typeof counting.getLastNumber === 'function' ? counting.getLastNumber() : null;
      if (currentProgress === undefined || currentProgress === null) currentProgress = 0;

      const targetNum = Number(goal.target);
      let percent = 0;

      if (targetNum === 0) {
        percent = currentProgress === 0 ? 100 : 0;
      } else if (targetNum > 0) {
        percent = Math.round((currentProgress / targetNum) * 100);
      } else { // negative target: measure progress from 0 down to target
        // Example: target -100, current -50 -> progress 50%
        const denom = (0 - targetNum);
        const numer = (0 - currentProgress);
        percent = denom === 0 ? 0 : Math.round((numer / denom) * 100);
      }

      // Clamp
      if (percent < 0) percent = 0;
      if (percent > 100) percent = 100;

      const pb = makeProgressBar(currentProgress, goal.target, 16);
      embed.addFields({ name: 'Progress (current number)', value: `${pb.bar} ${pb.percent}%\n${currentProgress} / ${goal.target}`, inline: false });
    }

    await interaction.editReply({ embeds: [embed] });
  }
};