// Migration script: import JSON files in ./data into SQLite database (data/counting.db)
// Safe to run multiple times; uses UPSERT/IGNORE where possible.
const fs = require('node:fs');
const path = require('node:path');
const { db, stmts } = require('./db');
const crypto = require('node:crypto');

const DATA_DIR = path.join(__dirname, 'data');

function readJsonSafe(filename, fallback) {
  const full = path.join(DATA_DIR, filename);
  try {
    if (!fs.existsSync(full)) return fallback;
    const txt = fs.readFileSync(full, 'utf8');
    return JSON.parse(txt);
  } catch (e) {
    console.warn(`WARN: Failed to read ${filename}:`, e.message);
    return fallback;
  }
}

function migrateCountingStats() {
  const stats = readJsonSafe('countingStats.json', {});
  // Cleanup any previously mis-imported rows (from early migration attempt)
  try {
    db.prepare("DELETE FROM users WHERE user_id IN ('shame','fame','currentStreak','bestStreak','dailyCounts','positiveCounts','negativeCounts')").run();
  } catch {}

  // Expected shape: { fame: [[userId, n], ...], shame: [[userId, n], ...], currentStreak: [[userId, n]...], bestStreak: [...], positiveCounts: [...], negativeCounts: [...] }
  const userMap = new Map();

  function setPairs(arr, field) {
    if (!Array.isArray(arr)) return;
    for (const pair of arr) {
      if (!Array.isArray(pair) || pair.length < 2) continue;
      const [uid, val] = pair;
      const id = String(uid);
      if (!userMap.has(id)) userMap.set(id, {});
      userMap.get(id)[field] = toInt(val, 0);
    }
  }

  setPairs(stats.fame, 'fame');
  setPairs(stats.shame, 'shame');
  setPairs(stats.currentStreak, 'current_streak');
  setPairs(stats.bestStreak, 'best_streak');
  setPairs(stats.positiveCounts, 'pos_counts');
  setPairs(stats.negativeCounts, 'neg_counts');

  const now = Date.now();
  let count = 0;
  const txn = db.transaction(() => {
    for (const [user_id, v] of userMap.entries()) {
      const row = {
        user_id,
        fame: toInt(v.fame, 0),
        shame: toInt(v.shame, 0),
        best_streak: toInt(v.best_streak, 0),
        current_streak: toInt(v.current_streak, 0),
        pos_counts: toInt(v.pos_counts, 0),
        neg_counts: toInt(v.neg_counts, 0),
        updated_at: now
      };
      stmts.upsertUser.run(row);
      count++;
    }
  });
  txn();
  return count;
}

function migrateAchievements() {
  const userAch = readJsonSafe('userAchievements.json', {});
  let count = 0;
  const txn = db.transaction((obj) => {
    for (const [userId, list] of Object.entries(obj)) {
      if (!Array.isArray(list)) continue;
      for (const item of list) {
        if (!item) continue;
        // Support plain string IDs or object with id/earned_at
        const achId = typeof item === 'string' ? item : (item.id ?? item.achievement_id);
        if (!achId) continue;
        const earned = typeof item === 'object' && item.earned_at
          ? toInt(item.earned_at, Date.now())
          : Date.now();
        stmts.insertAchievement.run({ user_id: String(userId), achievement_id: String(achId), earned_at: earned });
        count++;
      }
    }
  });
  txn(userAch);
  return count;
}

function migrateGoals() {
  const goals = readJsonSafe('goals.json', []);
  let count = 0;
  const arr = Array.isArray(goals) ? goals : Object.values(goals || {});
  const txn = db.transaction((rows) => {
    for (const g of rows) {
      if (!g) continue;
      const id = String(
        g.id ?? g.goal_id ?? g.name ?? deterministicId('goal', [
          g.text ?? g.description ?? '',
          g.target ?? '',
          g.pinned_message_id ?? g.pinnedMessageId ?? '',
          // created_at may be absent; do not use Date.now() in ID to keep it stable across runs
          g.created_at ?? g.createdAt ?? ''
        ])
      );
      const row = {
        id,
        text: orNull(g.text ?? g.description),
        target: toInt(g.target, null),
        pinned_message_id: orNull(g.pinned_message_id ?? g.pinnedMessageId),
        created_at: toInt(g.created_at ?? g.createdAt, null),
        completed_at: toInt(g.completed_at ?? g.completedAt, null)
      };
      stmts.upsertGoal.run(row);
      count++;
    }
  });
  txn(arr);
  return count;
}

function migrateSuggestions() {
  const suggestions = readJsonSafe('suggestions.json', []);
  let count = 0;
  const arr = Array.isArray(suggestions) ? suggestions : Object.values(suggestions || {});
  const txn = db.transaction((rows) => {
    for (const s of rows) {
      if (!s) continue;
      const user_id = s.user_id ? String(s.user_id) : (s.author_id ? String(s.author_id) : null);
      const text = (s.text ?? s.content ?? s.body ?? '').toString();
      const created_at = toInt(s.created_at ?? s.createdAt, null);
      const status = s.status ?? 'open';
      const id = String(
        s.id ?? deterministicId('sugg', [user_id ?? '', text.trim(), created_at ?? '', status])
      );
      const row = {
        id,
        user_id,
        text: orNull(text),
        status: orNull(status),
        // Do not default created_at to now; keep null so ID stays stable across runs
        created_at: created_at
      };
      stmts.upsertSuggestion.run(row);
      count++;
    }
  });
  txn(arr);
  return count;
}

function toInt(v, fallback) {
  if (v === null || v === undefined) return fallback;
  const n = Number(v);
  return Number.isFinite(n) ? Math.trunc(n) : fallback;
}

function orNull(v) {
  return v === undefined ? null : v;
}

function cryptoRandomId() {
  return 'id_' + Math.random().toString(36).slice(2, 10);
}

function deterministicId(prefix, parts) {
  const h = crypto.createHash('sha256');
  // Normalize parts as strings; avoid undefined
  for (const p of parts) h.update(String(p));
  // Shorten for readability but sufficient uniqueness
  return `${prefix}_${h.digest('hex').slice(0, 16)}`;
}

function main() {
  console.log('Starting migration from data/*.json into data/counting.db ...');
  const users = migrateCountingStats();
  console.log(`Users upserted: ${users}`);

  const ach = migrateAchievements();
  console.log(`Achievements inserted (unique pairs): ${ach}`);

  const goals = migrateGoals();
  console.log(`Goals upserted: ${goals}`);

  const sugg = migrateSuggestions();
  console.log(`Suggestions upserted: ${sugg}`);

  console.log('Migration complete.');
}

main();
