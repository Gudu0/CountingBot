const { SlashCommandBuilder, MessageFlags, AttachmentBuilder } = require('discord.js');
const ENV = require('../../data/config.json');
let sqlite;
try { sqlite = require('../../db'); } catch { sqlite = null; }

const X_AXES = ['day','user','last24h'];
const Y_METRICS = ['correct','incorrect','total'];
const RANGES = ['7d','30d','90d','all'];

function rangeToMs(r) {
    switch (r) {
        case '7d': return 7*24*60*60*1000;
        case '30d': return 30*24*60*60*1000;
        case '90d': return 90*24*60*60*1000;
        default: return null; // all
    }
}

function metricSql(expr) {
    switch (expr) {
        case 'correct': return 'SUM(CASE WHEN is_correct=1 THEN 1 ELSE 0 END)';
        case 'incorrect': return 'SUM(CASE WHEN is_correct=0 THEN 1 ELSE 0 END)';
        case 'total': return 'COUNT(*)';
        default: return 'COUNT(*)';
    }
}

module.exports = {
    data: new SlashCommandBuilder()
        .setName('graph')
        .setDescription('Render a graph from counting stats (SQL-backed)')
        .addStringOption(o => o.setName('x_axis').setDescription('X axis').setRequired(true).addChoices(
            ...X_AXES.map(v => ({ name: v, value: v }))
        ))
        .addStringOption(o => o.setName('y_metric').setDescription('Y metric').setRequired(true).addChoices(
            ...Y_METRICS.map(v => ({ name: v, value: v }))
        ))
        .addStringOption(o => o.setName('range').setDescription('Time range').setRequired(false).addChoices(
            ...RANGES.map(v => ({ name: v, value: v }))
        ))
        .addUserOption(o => o.setName('user').setDescription('Target user (for day x-axis)').setRequired(false))
        .addStringOption(o => o.setName('day').setDescription('Target day (YYYY-MM-DD) for user x-axis').setRequired(false))
        .addStringOption(o => o.setName('bucket').setDescription('Bucket size for last24h').setRequired(false).addChoices(
            { name: '5m', value: '5m' },
            { name: '10m', value: '10m' },
            { name: '15m', value: '15m' }
        ))
        .addBooleanOption(o => o.setName('include_deleted').setDescription('Include deleted messages').setRequired(false)),

    async execute(interaction) {
        await interaction.deferReply({ flags: MessageFlags.Ephemeral });

        if (!sqlite || !sqlite.db) {
            return interaction.editReply('SQLite is not available on this instance.');
        }
        // Using QuickChart HTTP rendering — no local native deps required

    const xAxis = interaction.options.getString('x_axis');
    const yMetric = interaction.options.getString('y_metric');
    const range = interaction.options.getString('range') || '30d';
    const includeDeleted = interaction.options.getBoolean('include_deleted');
    const targetUser = interaction.options.getUser('user') || interaction.user;
    const dayStr = interaction.options.getString('day');
    const bucketOpt = interaction.options.getString('bucket') || '10m';

        // Special mode: last 24 hours — show the count value every 10 minutes
    if (xAxis === 'last24h') {
            try {
        const bucketMs = bucketOpt === '5m' ? 5*60*1000 : (bucketOpt === '15m' ? 15*60*1000 : 10*60*1000);
                const now = Date.now();
                const startAligned = Math.floor((now - 24 * 60 * 60 * 1000) / bucketMs) * bucketMs;
                const endAligned = startAligned + 24 * 60 * 60 * 1000;

                const delCond = includeDeleted ? '1=1' : '(deleted IS NULL OR deleted=0)';

                // Prior value just before the window start
                const priorRow = sqlite.db.prepare(`
                    SELECT parsed_number FROM messages
                    WHERE guild_id=? AND ${delCond} AND is_correct=1 AND timestamp < ?
                    ORDER BY timestamp DESC LIMIT 1
                `).get(String(ENV.bs_server_id), startAligned);

                // All correct messages inside the window
                const msgRows = sqlite.db.prepare(`
                    SELECT timestamp, parsed_number FROM messages
                    WHERE guild_id=? AND ${delCond} AND is_correct=1 AND timestamp >= ? AND timestamp < ?
                    ORDER BY timestamp ASC
                `).all(String(ENV.bs_server_id), startAligned, endAligned);

                let cur = priorRow ? priorRow.parsed_number : null;
                let j = 0;
                const labels = [];
                const data = [];
                for (let t = startAligned; t < endAligned; t += bucketMs) {
                    while (j < msgRows.length && msgRows[j].timestamp < t + bucketMs) {
                        cur = msgRows[j].parsed_number;
                        j++;
                    }
                    labels.push(new Date(t).toISOString().slice(11, 16) + 'Z'); // HH:MMZ (UTC)
                    data.push(cur !== null ? Number(cur) : null);
                }

                if (data.every(v => v === null)) {
                    return interaction.editReply('No data in the last 24 hours.');
                }

                const width = 1000, height = 450;
                const config = {
                    type: 'line',
                    data: {
                        labels,
                        datasets: [{
                            label: 'Count value (every 10 min, last 24h, UTC)',
                            data,
                            borderColor: 'rgba(54, 162, 235, 1)',
                            backgroundColor: 'rgba(54, 162, 235, 0.15)',
                            stepped: true,
                            fill: false,
                            spanGaps: true,
                            pointRadius: 0
                        }]
                    },
                    options: {
                        responsive: false,
                        plugins: { legend: { display: true } },
                        scales: {
                            y: { beginAtZero: true },
                            x: { ticks: { autoSkip: true, maxRotation: 0, minRotation: 0 } }
                        }
                    }
                };

                const res = await fetch('https://quickchart.io/chart/create', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ chart: config, backgroundColor: 'white', width, height, format: 'png' })
                });
                if (!res.ok) {
                    return interaction.editReply('Graph render request failed: HTTP ' + res.status);
                }
                const json = await res.json();
                const imgUrl = json?.imageUrl || json?.url;
                if (!imgUrl) return interaction.editReply('Graph render failed: no image URL.');

                const imgRes = await fetch(imgUrl);
                if (!imgRes.ok) return interaction.editReply('Graph fetch failed: HTTP ' + imgRes.status);
                const arrayBuf = await imgRes.arrayBuffer();
                const buffer = Buffer.from(arrayBuf);
                const file = new AttachmentBuilder(buffer, { name: `graph-last24h.png` });
                await interaction.editReply({ content: `Last 24 hours (${bucketOpt} steps, UTC).`, files: [file] });
                return; // done
            } catch (e) {
                return interaction.editReply('Graph render failed: ' + e.message);
            }
        }

        // Build filters
        const params = [];
        const conds = ['guild_id=?'];
        params.push(String(ENV.bs_server_id));
        // No longer filter by channel_id; we stopped logging it for simplicity
        if (!includeDeleted) {
            conds.push('(deleted IS NULL OR deleted=0)');
        }
        let dayStart = null;
        let dayEnd = null;
        // For user x-axis, we use a single day window; for day x-axis, we apply range window.
        if (xAxis === 'user') {
            let useDay = dayStr;
            if (!useDay) {
                // Default to today (UTC)
                const now = new Date();
                const y = now.getUTCFullYear();
                const m = String(now.getUTCMonth() + 1).padStart(2, '0');
                const d = String(now.getUTCDate()).padStart(2, '0');
                useDay = `${y}-${m}-${d}`;
            }
            const m = useDay.match(/^(\d{4})-(\d{2})-(\d{2})$/);
            if (!m) {
                return interaction.editReply('Invalid day format. Use YYYY-MM-DD.');
            }
            const year = Number(m[1]);
            const month = Number(m[2]);
            const day = Number(m[3]);
            dayStart = Date.UTC(year, month - 1, day);
            dayEnd = Date.UTC(year, month - 1, day + 1);
            conds.push('timestamp>=? AND timestamp<?');
            params.push(dayStart, dayEnd);
        } else {
            // xAxis === 'day'
            const rangeMs = rangeToMs(range);
            if (rangeMs) {
                const since = Date.now() - rangeMs;
                conds.push('timestamp>=?');
                params.push(since);
            }
            // Also filter by user
            conds.push('author_id=?');
            params.push(String(targetUser.id));
        }
        const where = conds.length ? ('WHERE ' + conds.join(' AND ')) : '';

        const metric = metricSql(yMetric);
        let rows = [];
        try {
            if (xAxis === 'day') {
                const sql = `
                    SELECT strftime('%Y-%m-%d', datetime(timestamp/1000,'unixepoch')) AS bucket,
                                 ${metric} AS value
                    FROM messages
                    ${where}
                    GROUP BY bucket
                    ORDER BY bucket ASC
                    LIMIT 180
                `;
                rows = sqlite.db.prepare(sql).all(...params);
            } else if (xAxis === 'user') {
                const sql = `
                    SELECT author_id AS bucket,
                                 ${metric} AS value
                    FROM messages
                    ${where}
                    GROUP BY author_id
                    ORDER BY value DESC
                    LIMIT 50
                `;
                rows = sqlite.db.prepare(sql).all(...params);
            } else {
                return interaction.editReply('Unsupported x_axis.');
            }
        } catch (err) {
            return interaction.editReply('Query failed: ' + err.message);
        }

        if (!rows || rows.length === 0) {
            return interaction.editReply('No data for the selected options. Try a larger range or different filters.');
        }

        // If user x-axis, try to show usernames instead of IDs (best-effort, limited fetching)
        let labels;
        if (xAxis === 'user') {
            const ids = rows.map(r => String(r.bucket));
            const guild = interaction.guild;
            const names = new Map();
            if (guild && guild.members && guild.members.cache) {
                for (const id of ids) {
                    const m = guild.members.cache.get(id);
                    if (m) names.set(id, m.displayName || (m.user && (m.user.globalName || m.user.username)) || id);
                }
            }
            // fetch up to 20 unresolved
            const unresolved = Array.from(new Set(ids.filter(id => !names.has(id)))).slice(0, 20);
            for (const id of unresolved) {
                try {
                    let disp = null;
                    if (guild && guild.members) {
                        const m = await guild.members.fetch(id);
                        if (m) disp = m.displayName || (m.user && (m.user.globalName || m.user.username));
                    }
                    if (!disp && interaction.client && interaction.client.users) {
                        const u = await interaction.client.users.fetch(id);
                        if (u) disp = u.globalName || u.username || u.tag;
                    }
                    if (disp) names.set(id, disp);
                } catch {}
            }
            labels = ids.map(id => names.get(id) || id);
        } else {
            labels = rows.map(r => String(r.bucket));
        }
        const data = rows.map(r => Number(r.value || 0));

        const width = 900, height = 450;
        const config = {
            type: 'bar',
            data: {
                labels,
                datasets: [{
                    label: xAxis === 'day'
                        ? `${yMetric} by day for @${targetUser.username || targetUser.id}`
                        : `${yMetric} by user for ${(dayStr || (new Date(dayStart)).toISOString().slice(0,10))}`,
                    data,
                    backgroundColor: 'rgba(54, 162, 235, 0.6)'
                }]
            },
            options: {
                responsive: false,
                plugins: { legend: { display: true } },
                scales: {
                    y: { beginAtZero: true },
                    x: { ticks: { autoSkip: true, maxRotation: 45, minRotation: 0 } }
                }
            }
        };

        // Render via QuickChart
        try {
            const res = await fetch('https://quickchart.io/chart/create', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ chart: config, backgroundColor: 'white', width, height, format: 'png' })
            });
            if (!res.ok) {
                return interaction.editReply('Graph render request failed: HTTP ' + res.status);
            }
            const json = await res.json();
            const imgUrl = json?.imageUrl || json?.url;
            if (!imgUrl) return interaction.editReply('Graph render failed: no image URL.');

            const imgRes = await fetch(imgUrl);
            if (!imgRes.ok) return interaction.editReply('Graph fetch failed: HTTP ' + imgRes.status);
            const arrayBuf = await imgRes.arrayBuffer();
            const buffer = Buffer.from(arrayBuf);
            const file = new AttachmentBuilder(buffer, { name: `graph-${xAxis}-${yMetric}.png` });
            const meta = xAxis === 'day'
                ? `user=${targetUser.tag || targetUser.id}, range=${range}`
                : `day=${(dayStr || (new Date(dayStart)).toISOString().slice(0,10))}`;
            await interaction.editReply({ content: `Here is your graph (${xAxis} vs ${yMetric}; ${meta}).`, files: [file] });
        } catch (e) {
            await interaction.editReply('Graph render failed: ' + e.message);
        }
    }
};