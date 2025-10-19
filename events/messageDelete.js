module.exports = {
    name: 'messageDelete',
    async execute(message) {
        const ENV = require('../config.json');
        const counting = require('../counting.js');
        const logger = require('../logger.js');

        // Only act if it's the counting channel
        if (message.channel.id !== ENV.bs_counting_id) return;

        try {
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
                }
            }
        } catch (err) {
            logger.log(`Error updating lastNumber after deletion: ${err.message}`, 'error_updating_last_after_deletion', ENV.bs_server_id);
        }
    }
};