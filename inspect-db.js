// Simple DB inspection tool: prints counts and sample rows
const path = require('node:path');
const Database = require('better-sqlite3');

const DB_PATH = path.join(__dirname, 'data', 'counting.db');
const db = new Database(DB_PATH, { readonly: true });

function printHeader(title) {
  console.log(`\n=== ${title} ===`);
}

function tableExists(name) {
  const row = db.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name=?").get(name);
  return !!row;
}

function main() {
  printHeader('Database Info');
  const pragma = db.prepare('PRAGMA integrity_check').get();
  console.log('Integrity check:', pragma['integrity_check'] || Object.values(pragma)[0]);

  const tables = ['users', 'messages', 'achievements', 'goals', 'suggestions'];
  for (const t of tables) {
    if (!tableExists(t)) {
      console.log(`${t}: (table missing)`);
      continue;
    }
    const cnt = db.prepare(`SELECT COUNT(*) as c FROM ${t}`).get().c;
    console.log(`${t}: ${cnt}`);
  }

  if (tableExists('users')) {
    printHeader('Top users by fame (up to 10)');
    const rows = db.prepare('SELECT user_id, fame, shame, best_streak FROM users ORDER BY fame DESC LIMIT 10').all();
    console.table(rows);
  }

  if (tableExists('messages')) {
    printHeader('Most recent messages (up to 5)');
    const rows = db.prepare('SELECT message_id, author_id, timestamp, parsed_number, is_correct FROM messages ORDER BY timestamp DESC LIMIT 5').all();
    console.table(rows);
  }

  printHeader('Done');
}

main();
