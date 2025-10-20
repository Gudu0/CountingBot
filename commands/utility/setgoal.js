const { SlashCommandBuilder, PermissionFlagsBits, EmbedBuilder, MessageFlags } = require('discord.js');
const fs = require('fs');
const path = require('path');

const goalsPath = path.join(__dirname, '../../data/goals.json');
const configPath = path.join(__dirname, '../../data/config.json');

function loadGoals() {
  if (!fs.existsSync(goalsPath)) return { current: null };
  try { return JSON.parse(fs.readFileSync(goalsPath, 'utf8')); } catch (e) { return { current: null }; }
}

function saveGoals(data) {
  fs.writeFileSync(goalsPath, JSON.stringify(data, null, 2), 'utf8');
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
  cooldown: 5,
  data: new SlashCommandBuilder()
    .setName('setgoal')
    .setDescription('Set the current counting goal (admin only)')
    .addStringOption(opt => opt.setName('text').setDescription('Goal description').setRequired(true))
    .addIntegerOption(opt => opt.setName('target').setDescription('Target number (optional)').setRequired(true))
    .addStringOption(opt => opt.setName('deadline').setDescription('Deadline (ISO or natural language, optional)').setRequired(true))
    .setDefaultMemberPermissions(PermissionFlagsBits.BanMembers),

  async execute(interaction) {
    await interaction.deferReply({ flags: MessageFlags.Ephemeral });

    const text = interaction.options.getString('text');
    const target = interaction.options.getInteger('target');
    const deadlineRaw = interaction.options.getString('deadline');
    const setBy = interaction.user.id;
    let deadline = null;
    let deadlineDate = null;
    if (deadlineRaw) {
      // Accept numeric epoch (seconds or ms) or ISO/natural date
      if (/^\d+$/.test(deadlineRaw)) {
        const n = Number(deadlineRaw);
        deadlineDate = String(deadlineRaw).length <= 10 ? new Date(n * 1000) : new Date(n);
      } else {
        const parsed = Date.parse(deadlineRaw);
        if (!isNaN(parsed)) deadlineDate = new Date(parsed);
      }
      if (deadlineDate && !isNaN(deadlineDate)) deadline = deadlineDate.toISOString(); else deadline = deadlineRaw;
    }

    const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    const countingChannelId = config.bs_counting_id || config.test_counting_id;
    if (!countingChannelId) {
      await interaction.editReply('Counting channel is not configured. Please set it in data/config.json');
      return;
    }

    const goals = loadGoals();
    const newGoal = {
      text,
      target: typeof target === 'number' ? target : null,
      deadline,
      setBy,
      createdAt: new Date().toISOString(),
      pinnedMessageId: goals.current && goals.current.pinnedMessageId ? goals.current.pinnedMessageId : null
    };

    let displayDeadline = 'None';
    if (newGoal.deadline) {
      let dlDate = null;
      if (/^\d+$/.test(String(newGoal.deadline))) {
        const n = Number(newGoal.deadline);
        dlDate = String(newGoal.deadline).length <= 10 ? new Date(n * 1000) : new Date(n);
      } else {
        const parsed = Date.parse(newGoal.deadline);
        if (!isNaN(parsed)) dlDate = new Date(parsed);
      }
      if (dlDate && !isNaN(dlDate)) {
        displayDeadline = `<t:${Math.floor(dlDate.getTime() / 1000)}:R>`;
      } else {
        displayDeadline = String(newGoal.deadline);
      }
    }
    // Build embed
    const embed = new EmbedBuilder()
      .setTitle('Counting Goal')
      .setDescription(newGoal.text)
      .setTimestamp(new Date(newGoal.createdAt)) // pass a Date, not an ISO string
      .setColor(0x00AE86)
      .addFields(
        { name: 'Set by', value: `<@${newGoal.setBy}>`, inline: true },
        { name: 'Target', value: newGoal.target ? String(newGoal.target) : 'None', inline: true },
        { name: 'Deadline', value: displayDeadline, inline: true }
      );

    if (newGoal.target !== undefined && newGoal.target !== null) {
      // show progress bar using current lastNumber if available
      let currentProgress = 0;
      try {
        const counting = require('../../counting.js');
        if (counting && typeof counting.getLastNumber === 'function') currentProgress = counting.getLastNumber() || 0;
      } catch (e) { currentProgress = 0; }
      const pb = makeProgressBar(currentProgress, newGoal.target, 16);
      embed.addFields({ name: 'Progress', value: `${pb.bar} ${pb.percent}%\n${currentProgress} / ${newGoal.target}`, inline: false });
      // Initialize lastReportedPercent so counting.js can skip initial updates
      newGoal.lastReportedPercent = pb.percent || 0;
    }

    // Post or update pinned message in counting channel
    try {
      const channel = await interaction.client.channels.fetch(countingChannelId).catch(() => null);
      if (!channel) {
        await interaction.editReply('Could not find the counting channel. Check the channel id in config.');
        return;
      }

      let pinnedMessage = null;
      if (newGoal.pinnedMessageId) {
        try { pinnedMessage = await channel.messages.fetch(newGoal.pinnedMessageId).catch(() => null); } catch(e) { pinnedMessage = null; }
      }

      if (pinnedMessage && !pinnedMessage.deleted) {
        await pinnedMessage.edit({ embeds: [embed] });
      } else {
        const sent = await channel.send({ embeds: [embed] });
        // Pin it
        try { await sent.pin(); newGoal.pinnedMessageId = sent.id; } catch (err) { /* ignore pin failures */ }
      }
    } catch (err) {
      console.error('Error posting goal message:', err);
      // Continue — we still save the goal so /goal works
    }

    goals.current = newGoal;
    saveGoals(goals);

    await interaction.editReply('Goal saved and pinned (if permissions allowed).');
  }
};
