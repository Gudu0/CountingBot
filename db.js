// SQLite database setup for CountingBot
// Uses better-sqlite3 (synchronous, fast, zero external service)
const Database = require('better-sqlite3');
const path = require('node:path');

const DB_PATH = path.join(__dirname, 'data', 'counting.db');
const db = new Database(DB_PATH);

// Improve concurrency and durability
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

// Bootstrap schema (safe to run at every startup)
db.exec(`
CREATE TABLE IF NOT EXISTS messages (
  message_id TEXT PRIMARY KEY,
  author_id TEXT,
  guild_id TEXT,
  channel_id TEXT,
  timestamp INTEGER,
  content TEXT,
  message_length INTEGER,
  is_numeric INTEGER,
  parsed_number INTEGER,
  has_leading_zero INTEGER,
  number_delta INTEGER,
  is_correct INTEGER,
  hour INTEGER,
  weekday INTEGER,
  mentions_count INTEGER,
  attachments_count INTEGER,
  roles_count INTEGER,
  deleted INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS users (
  user_id TEXT PRIMARY KEY,
  fame INTEGER NOT NULL DEFAULT 0,
  shame INTEGER NOT NULL DEFAULT 0,
  best_streak INTEGER NOT NULL DEFAULT 0,
  current_streak INTEGER NOT NULL DEFAULT 0,
  pos_counts INTEGER NOT NULL DEFAULT 0,
  neg_counts INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS achievements (
  user_id TEXT NOT NULL,
  achievement_id TEXT NOT NULL,
  earned_at INTEGER,
  PRIMARY KEY (user_id, achievement_id)
);

CREATE TABLE IF NOT EXISTS goals (
  id TEXT PRIMARY KEY,
  text TEXT,
  target INTEGER,
  pinned_message_id TEXT,
  created_at INTEGER,
  completed_at INTEGER,
  set_by TEXT,
  deadline TEXT,
  last_reported_percent INTEGER DEFAULT 0,
  completed_by TEXT
);

CREATE TABLE IF NOT EXISTS suggestions (
  id TEXT PRIMARY KEY,
  user_id TEXT,
  text TEXT,
  status TEXT,
  created_at INTEGER
);

CREATE TABLE IF NOT EXISTS kv (
  key TEXT PRIMARY KEY,
  value TEXT
);

CREATE INDEX IF NOT EXISTS idx_messages_ts ON messages(timestamp);
CREATE INDEX IF NOT EXISTS idx_messages_author_ts ON messages(author_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_messages_correct_ts ON messages(is_correct, timestamp);
CREATE INDEX IF NOT EXISTS idx_messages_guild_channel_ts ON messages(guild_id, channel_id, timestamp);
`);


// Lightweight schema migrations for columns that may be missing
try {
  const cols = db.prepare(`PRAGMA table_info(messages)`).all();
  const hasDeleted = cols.some(c => c.name === 'deleted');
  if (!hasDeleted) {
    db.exec(`ALTER TABLE messages ADD COLUMN deleted INTEGER DEFAULT 0;`);
  }
} catch {}

// Migrate goals table additional columns if needed (safe, idempotent)
try {
  const gcols = db.prepare(`PRAGMA table_info(goals)`).all();
  const need = (name) => !gcols.some(c => c.name === name);
  const alters = [];
  if (need('set_by')) alters.push(`ALTER TABLE goals ADD COLUMN set_by TEXT;`);
  if (need('deadline')) alters.push(`ALTER TABLE goals ADD COLUMN deadline TEXT;`);
  if (need('last_reported_percent')) alters.push(`ALTER TABLE goals ADD COLUMN last_reported_percent INTEGER DEFAULT 0;`);
  if (need('completed_by')) alters.push(`ALTER TABLE goals ADD COLUMN completed_by TEXT;`);
  if (alters.length) db.exec(alters.join('\n'));
} catch {}
// Prepared statements used by migration and future writes
const stmts = {
  insertMessage: db.prepare(`
    INSERT OR REPLACE INTO messages (
      message_id, author_id, guild_id, timestamp, content, message_length,
      is_numeric, parsed_number, has_leading_zero, number_delta, is_correct,
      hour, weekday
    ) VALUES (
      @message_id, @author_id, @guild_id, @timestamp, @content, @message_length,
      @is_numeric, @parsed_number, @has_leading_zero, @number_delta, @is_correct,
      @hour, @weekday
    );
  `),
  upsertUser: db.prepare(`
    INSERT INTO users (user_id, fame, shame, best_streak, current_streak, pos_counts, neg_counts, updated_at)
    VALUES (@user_id, @fame, @shame, @best_streak, @current_streak, @pos_counts, @neg_counts, @updated_at)
    ON CONFLICT(user_id) DO UPDATE SET
      fame=excluded.fame,
      shame=excluded.shame,
      best_streak=excluded.best_streak,
      current_streak=excluded.current_streak,
      pos_counts=excluded.pos_counts,
      neg_counts=excluded.neg_counts,
      updated_at=excluded.updated_at;
  `),
  upsertGoal: db.prepare(`
    INSERT INTO goals (id, text, target, pinned_message_id, created_at, completed_at, set_by, deadline, last_reported_percent, completed_by)
    VALUES (@id, @text, @target, @pinned_message_id, @created_at, @completed_at, @set_by, @deadline, @last_reported_percent, @completed_by)
    ON CONFLICT(id) DO UPDATE SET
      text=excluded.text,
      target=excluded.target,
      pinned_message_id=excluded.pinned_message_id,
      created_at=excluded.created_at,
      completed_at=excluded.completed_at,
      set_by=COALESCE(excluded.set_by, set_by),
      deadline=COALESCE(excluded.deadline, deadline),
      last_reported_percent=COALESCE(excluded.last_reported_percent, last_reported_percent),
      completed_by=COALESCE(excluded.completed_by, completed_by);
  `),
  insertAchievement: db.prepare(`
    INSERT OR IGNORE INTO achievements (user_id, achievement_id, earned_at)
    VALUES (@user_id, @achievement_id, @earned_at);
  `),
  getGoalById: db.prepare(`
    SELECT id, text, target, pinned_message_id, created_at, completed_at, set_by, deadline, last_reported_percent, completed_by
    FROM goals WHERE id=?
  `),
  updateGoalProgress: db.prepare(`
    UPDATE goals SET last_reported_percent=? WHERE id=?
  `),
  completeGoal: db.prepare(`
    UPDATE goals SET completed_at=?, completed_by=? WHERE id=?
  `),
  upsertSuggestion: db.prepare(`
    INSERT INTO suggestions (id, user_id, text, status, created_at)
    VALUES (@id, @user_id, @text, @status, @created_at)
    ON CONFLICT(id) DO UPDATE SET
      user_id=excluded.user_id,
      text=excluded.text,
      status=excluded.status,
      created_at=excluded.created_at;
  `),
  markMessageDeleted: db.prepare(`
    UPDATE messages SET deleted=1 WHERE message_id=?;
  `),
  getKV: db.prepare(`
    SELECT value FROM kv WHERE key=?;
  `),
  setKV: db.prepare(`
    INSERT INTO kv (key, value) VALUES (@key, @value)
    ON CONFLICT(key) DO UPDATE SET value=excluded.value;
  `)
};

module.exports = {
  db,
  stmts
};
