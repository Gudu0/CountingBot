module.exports = {
    name: 'messageDelete',
    async execute(message) {
    const ENV = require('../data/config.json');
    const counting = require('../counting.js');
    const logger = require('../logger.js');
    let sqlite;
    try { sqlite = require('../db'); } catch { sqlite = null; }

        // Only act if it's the counting channel
        if (message.channel.id !== ENV.bs_counting_id) return;

        try {
            // Mark as deleted in SQLite if present
            try {
                if (sqlite && sqlite.stmts && sqlite.stmts.markMessageDeleted) {
                    sqlite.stmts.markMessageDeleted.run(String(message.id));
                }
            } catch (e) {
                try { logger.log(`SQLite markMessageDeleted error: ${e.message}`, 'sqlite_error', ENV.bs_server_id); } catch {}
            }

            // Fetch the last message in the channel
            const messages = await message.channel.messages.fetch({ limit: 1 });
            const lastMsg = messages.first();
            if (lastMsg) {
                const num = parseInt(lastMsg.content.trim());
                const id = lastMsg.author.id;
                if (!isNaN(num)) {
                    counting.setLastNumber(num);
                    counting.setLastUser(id);
                    logger.log(`After deletion, reset lastNumber to ${num} and lastUser to ${id}.`, 'resetting_last_after_deletion', ENV.bs_server_id);
                    // Persist runtime state to SQLite KV if available
                    try {
                        if (sqlite && sqlite.stmts && sqlite.stmts.setKV) {
                            sqlite.stmts.setKV.run({ key: 'last_number', value: String(num) });
                            sqlite.stmts.setKV.run({ key: 'last_user', value: String(id) });
                        }
                    } catch {}
                }
            }
        } catch (err) {
            logger.log(`Error updating lastNumber after deletion: ${err.message}`, 'error_updating_last_after_deletion', ENV.bs_server_id);
        }
    }
};