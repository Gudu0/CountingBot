const fs = require('node:fs');
const path = require('node:path');
const logger = require('./logger.js');
const crypto = require('node:crypto');
const achievements = require('./achievement-check.js');
let sqlite;
try { sqlite = require('./db'); } catch { sqlite = null; }

function readJSON(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function writeJSON(filePath, data) {
  fs.writeFileSync(filePath, JSON.stringify(data, null, 2), 'utf8');
}

function getCountingChannelId() {
  try {
    const cfg = readJSON(path.join(__dirname, 'data', 'config.json'));
    return cfg.test_counting_id || cfg.bs_counting_id;
  } catch {
    return null;
  }
}

function computePercent(current, target) {
  const t = Number(target);
  if (isNaN(t) || t === 0) return 0;
  let percent = 0;
  if (t > 0) percent = Math.round((current / t) * 100);
  else {
    const denom = (0 - t);
    const numer = (0 - current);
    percent = denom === 0 ? 0 : Math.round((numer / denom) * 100);
  }
  if (percent < 0) percent = 0;
  if (percent > 100) percent = 100;
  return percent;
}

function makeBar(percent, size = 16) {
  const fill = Math.round((percent / 100) * size);
  return '█'.repeat(fill) + '░'.repeat(size - fill);
}

async function updatePinnedProgress({ client, channelId, pinnedMessageId, goal, currentPercent, currentNumber }) {
  try {
    if (!client || !channelId || !pinnedMessageId) return false;
    const channel = await client.channels.fetch(channelId).catch(() => null);
    if (!channel) return false;
    const msg = await channel.messages.fetch(pinnedMessageId).catch(() => null);
    if (!msg) return false;
    const { EmbedBuilder } = require('discord.js');
    const displayDeadline = goal.deadline ? (isNaN(new Date(goal.deadline)) ? String(goal.deadline) : new Date(goal.deadline).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })) : 'None';
    const pb = makeBar(currentPercent, 16);
    const newEmbed = new EmbedBuilder()
      .setTitle('Counting Goal')
      .setDescription(goal.text)
      .setTimestamp(new Date(goal.createdAt || Date.now()))
      .setColor(0x00AE86)
      .addFields(
        { name: 'Set by', value: `<@${goal.setBy}>`, inline: true },
        { name: 'Target', value: goal.target ? String(goal.target) : 'None', inline: true },
        { name: 'Deadline', value: displayDeadline, inline: true },
        { name: 'Progress', value: `${pb} ${currentPercent}%\n${currentNumber} / ${goal.target}`, inline: false }
      );
    await msg.edit({ embeds: [newEmbed] }).catch(() => null);
    return true;
  } catch (err) {
    console.error('Error updating pinned goal message (progress):', err);
    return false;
  }
}

async function updatePinnedCompleted({ client, channelId, pinnedMessageId, goal, completedAt, completedBy }) {
  try {
    if (!client || !channelId || !pinnedMessageId) return false;
    const channel = await client.channels.fetch(channelId).catch(() => null);
    if (!channel) return false;
    const msg = await channel.messages.fetch(pinnedMessageId).catch(() => null);
    if (!msg) return false;
    const { EmbedBuilder } = require('discord.js');
    const newEmbed = new EmbedBuilder()
      .setTitle('Counting Goal')
      .setDescription(goal.text)
      .setTimestamp(new Date().toISOString())
      .setColor(0x00AE86)
      .addFields(
        { name: 'Set by', value: `<@${goal.setBy}>`, inline: true },
        { name: 'Target', value: goal.target ? String(goal.target) : 'None', inline: true },
        { name: 'Deadline', value: goal.deadline ? String(goal.deadline) : 'None', inline: true },
        { name: 'Completed', value: `Yes — <@${completedBy}> at ${completedAt}`, inline: false }
      );
    await msg.edit({ embeds: [newEmbed] }).catch(() => null);
    return true;
  } catch (err) {
    console.error('Error updating pinned goal message:', err);
    return false;
  }
}

async function handleGoalProgress({ lastNumber, author, client, thresholdPercent = 1, envServerId }) {
  const goalsPath = path.join(__dirname, 'data', 'goals.json');
  if (!fs.existsSync(goalsPath)) return { updatedPin: false, completed: false };

  let goalsData;
  try {
    goalsData = readJSON(goalsPath);
  } catch (err) {
    console.error('Error reading goals.json:', err);
    return { updatedPin: false, completed: false };
  }

  const goal = goalsData.current;
  if (!goal || goal.completedAt || goal.target === null || goal.target === undefined) {
    return { updatedPin: false, completed: false };
  }

  const target = Number(goal.target);
  if (isNaN(target)) return { updatedPin: false, completed: false };

  let reached = false;
  if (target >= 0) {
    if (lastNumber >= target) reached = true;
  } else {
    if (lastNumber <= target) reached = true;
  }

  const currentPercent = computePercent(lastNumber, goal.target);
  const lastReported = Number(goal.lastReportedPercent || 0);
  const shouldUpdatePin = (currentPercent - lastReported) >= thresholdPercent;
  let updatedPin = false;
  const channelId = getCountingChannelId();

  if (shouldUpdatePin && goal.pinnedMessageId && client) {
    updatedPin = await updatePinnedProgress({
      client,
      channelId,
      pinnedMessageId: goal.pinnedMessageId,
      goal,
      currentPercent,
      currentNumber: lastNumber
    });
    if (updatedPin) {
      goal.lastReportedPercent = currentPercent;
      try { writeJSON(goalsPath, goalsData); } catch {}
      try {
        if (sqlite && sqlite.stmts && sqlite.stmts.setKV) {
          sqlite.stmts.setKV.run({ key: 'goal_current_last_percent', value: String(currentPercent) });
        }
        // Also persist progress percent to SQL goal row when possible
        try {
          if (sqlite && sqlite.stmts && sqlite.stmts.updateGoalProgress) {
            const id = crypto.createHash('sha1').update(`${goal.text}|${goal.target}|${goal.createdAt}`).digest('hex');
            sqlite.stmts.updateGoalProgress.run(currentPercent, id);
          }
        } catch {}
      } catch {}
    }
  }

  if (reached) {
    const completedAt = new Date().toISOString();
    goal.completedAt = completedAt;
    goal.completedBy = author;

    if (goal.pinnedMessageId && channelId && client) {
      await updatePinnedCompleted({
        client,
        channelId,
        pinnedMessageId: goal.pinnedMessageId,
        goal,
        completedAt,
        completedBy: author
      });
    }

    try { writeJSON(goalsPath, goalsData); } catch {}
    try {
      if (sqlite && sqlite.stmts && sqlite.stmts.setKV) {
        sqlite.stmts.setKV.run({ key: 'goal_last_completed_at', value: String(completedAt) });
        sqlite.stmts.setKV.run({ key: 'goal_last_completed_by', value: String(author) });
      }
      // Also mark completion in SQL when possible
      try {
        if (sqlite && sqlite.stmts && sqlite.stmts.completeGoal) {
          const id = crypto.createHash('sha1').update(`${goal.text}|${goal.target}|${goal.createdAt}`).digest('hex');
          const ts = Date.now();
          sqlite.stmts.completeGoal.run(ts, String(author), id);
        }
      } catch {}
    } catch {}

    try { logger.log(`Goal reached: ${goal.text} — final number ${lastNumber} (by <@${author}>)`, 'goal_completed', envServerId); } catch {}
    try { achievements.awardAchievement(author, 'goal_winner'); } catch {}

    return { updatedPin, completed: true };
  }

  return { updatedPin, completed: false };
}

module.exports = {
  handleGoalProgress,
  computePercent, // exported for potential reuse/testing
  makeBar
};
