const fs = require('node:fs');
const { get } = require('node:http');
const path = require('node:path');
const ENV = require("./data/config.json");
const logger = require('./logger.js');
const achievements = require('./achievement-check.js');
const goalProgress = require('./goal-progress.js');
let sqlite;
try {
    // Lazy import SQLite statements; keep bot running even if DB missing
    sqlite = require('./db');
} catch (_) {
    sqlite = null;
}

function getConfig() {
    return JSON.parse(fs.readFileSync(path.join(__dirname, 'data', 'config.json'), 'utf8'));
}

let lastNumber;
let lastUser;
let mapOfShame = new Map();
let mapOfFame = new Map();
let currentStreak = new Map();
let bestStreak = new Map();
let dailyCounts = new Map();
let lastCountTime = new Map();
let positiveCounts = new Map();
let negativeCounts = new Map();
const GOAL_UPDATE_PERCENT_THRESHOLD = 1; // percent change required to edit pinned embed

function upsertUserStats(author) {
    if (!sqlite || !sqlite.stmts || !sqlite.stmts.upsertUser) return;
    try {
        const row = {
            user_id: String(author),
            fame: mapOfFame.get(author) || 0,
            shame: mapOfShame.get(author) || 0,
            best_streak: bestStreak.get(author) || 0,
            current_streak: currentStreak.get(author) || 0,
            pos_counts: positiveCounts.get(author) || 0,
            neg_counts: negativeCounts.get(author) || 0,
            updated_at: Date.now()
        };
        sqlite.stmts.upsertUser.run(row);
    } catch (err) {
        // Do not crash counting on DB issues; just log
        try { logger.log(`SQLite upsertUser error: ${err.message}`, 'sqlite_error', ENV.bs_server_id); } catch {}
    }
}

function toIntBool(v) { return v ? 1 : 0; }

function logMessageToSQLite(d, extras) {
    if (!sqlite || !sqlite.stmts || !sqlite.stmts.insertMessage) return;
    try {
        const content = (d.content || '').toString();
        const now = Date.now();
        const parsedNum = (typeof extras.parsedNumber === 'number' && !isNaN(extras.parsedNumber)) ? extras.parsedNumber : null;
        const deltaNum = (typeof extras.numberDelta === 'number' && isFinite(extras.numberDelta)) ? extras.numberDelta : null;
        const row = {
            message_id: String(d.id || ''),
            author_id: String(d.author?.id || extras.author || ''),
            guild_id: String(d.guild_id || ''),
            timestamp: now,
            content,
            message_length: content.length,
            is_numeric: toIntBool(!!extras.isNumeric),
            parsed_number: parsedNum,
            has_leading_zero: toIntBool(!!extras.hasLeadingZero),
            number_delta: deltaNum,
            is_correct: toIntBool(!!extras.isCorrect),
            hour: new Date(now).getHours(),
            weekday: new Date(now).getDay()
        };
        sqlite.stmts.insertMessage.run(row);
    } catch (err) {
        try { logger.log(`SQLite insertMessage error: ${err.message}`, 'sqlite_error', ENV.bs_server_id); } catch {}
    }
}

function saveRuntimeState() {
    if (!sqlite || !sqlite.stmts || !sqlite.stmts.setKV) return;
    try {
        if (typeof lastNumber === 'number' && lastUser) {
            sqlite.stmts.setKV.run({ key: 'last_number', value: String(lastNumber) });
            sqlite.stmts.setKV.run({ key: 'last_user', value: String(lastUser) });
        }
    } catch (err) {
        try { logger.log(`SQLite setKV error: ${err.message}`, 'sqlite_error', ENV.bs_server_id); } catch {}
    }
}

function tryLoadRuntimeState() {
    if (!sqlite || !sqlite.stmts || !sqlite.stmts.getKV) return;
    try {
        const lastNumRow = sqlite.stmts.getKV.get('last_number');
        const lastUsrRow = sqlite.stmts.getKV.get('last_user');
        if (lastNumRow && typeof lastNumRow.value === 'string') {
            const n = Number(lastNumRow.value);
            if (!isNaN(n)) lastNumber = n;
        }
        if (lastUsrRow && typeof lastUsrRow.value === 'string') {
            lastUser = lastUsrRow.value;
        }
    } catch (err) {
        // ignore, continue with defaults
    }
}

// Attempt to load runtime state on module load
tryLoadRuntimeState();


async function newMessage(d, ENV, client) {
    let author = d.author.id;
    let content = d.content.trim();
    

    if (d.guild_id !== ENV.bs_server_id) {return;}
    if (d.channel_id !== ENV.bs_counting_id) {return;}  
    
    let shouldRunAchievements = false; // flips true once we begin count processing

    try {
        shouldRunAchievements = true;

        // Check if content is a valid number
        if (!/^-?\d+$/.test(content)) {
            // Mark as incorrect
            incorrectCount(d, author, NaN, ENV, client);
            logger.log(`${author} sent a non-numeric message!`, 'non_numeric', ENV.bs_server_id);
            return;
        }
        if ((/^-?0\d+/.test(content))) { // matches "01", "-01", "0002", etc.
            incorrectCount(d, author, NaN, ENV, client);
            logger.log(`${author} sent a number with leading zeros!`, 'leading_zeros', ENV.bs_server_id);
            return;
        }

        //in the counting channel in bsg
        let newNumber = parseInt(content, 10); 

        const now = Date.now();
        const config = getConfig(); // reload config to get latest delay
        if (lastCountTime.has(author) && now - lastCountTime.get(author) < config.counting_delay) {
            // Too soon, mark as incorrect
            incorrectCount(d, author, newNumber, ENV, client);
            logger.log(`${author} is counting too fast!`, 'counted_too_fast', ENV.bs_server_id);
            return;
        }
        if ((lastNumber + 1 === newNumber || lastNumber - 1 === newNumber) && lastUser !== author) {
            //correct

            await correctCount(d, author, newNumber, client);
        } else if (lastNumber === undefined) {
            //something went wrong
            logger.log('last number not loaded properly or broken', 'last_number_not_loaded', ENV.bs_server_id);
            lastNumber = newNumber;
            lastUser = author;
            saveRuntimeState();
        } else {
            //incorrect
            incorrectCount(d, author, newNumber, ENV, client);
        } 
    } finally {
        if (shouldRunAchievements) {
            achievements.checkAndAwardAchievements(d, mapOfFame, currentStreak, positiveCounts, negativeCounts);
        }
    }
}

async function correctCount(d, author, newNumber, client ) {

    // Store previous lastNumber for positive/negative check
    const prevLastNumber = lastNumber;

    //set most recent number and user ------------------------------
    lastNumber = newNumber;
    lastUser = author;
    saveRuntimeState();

    //update last count time --------------------------------------
    lastCountTime.set(author, Date.now());

    //mapOfFame update ---------------------------------------------
    if (!mapOfFame.has(author)) {
        mapOfFame.set(author, 0);
    }
    mapOfFame.set(author, mapOfFame.get(author) + 1);
    saveStats();

    //positive/negative counts update -----------------------------
    if (newNumber === prevLastNumber + 1) {
        positiveCounts.set(author, (positiveCounts.get(author) || 0) + 1);
    } else if (newNumber === prevLastNumber - 1) {
        negativeCounts.set(author, (negativeCounts.get(author) || 0) + 1);
    }
    saveStats();

    //Streak update ------------------------------------------------
    currentStreak.set(author, (currentStreak.get(author) || 0) + 1);
    if ((bestStreak.get(author) || 0) < currentStreak.get(author)) {
        bestStreak.set(author, currentStreak.get(author));
    }

    // Dual-write to SQLite users table
    upsertUserStats(author);

    //Logging ------------------------------------------------------
    logger.log(`${author} had a correct number, with ${newNumber}! They now have ${mapOfFame.get(author)} correct counts.`, 'correct_count', ENV.bs_server_id);

    // Log message to SQLite (correct)
    logMessageToSQLite(d, {
        author,
        isNumeric: true,
        parsedNumber: newNumber,
        hasLeadingZero: false,
        numberDelta: (typeof prevLastNumber === 'number') ? (newNumber - prevLastNumber) : null,
        isCorrect: true
    });

    //Daily counts update -------------------------------------------
    const today = new Date().toISOString().slice(0, 10); // "YYYY-MM-DD"
    dailyCounts.set(today, (dailyCounts.get(today) || 0) + 1);

    // Check for active goal completion (delegated)
    try {
        await goalProgress.handleGoalProgress({
            lastNumber,
            author,
            client,
            thresholdPercent: GOAL_UPDATE_PERCENT_THRESHOLD,
            envServerId: ENV.bs_server_id
        });
    } catch (err) {
        console.error('Error checking goal completion:', err);
    }

}

function incorrectCount(d, author, newNumber, ENV, client) {
    // If the bot sent the message, don't delete or penalize it
    if (client && client.user && author === client.user.id) {
        logger.log(`Skipping incorrect handling for bot message ${d.id}`, 'skip_bot_message', ENV.bs_server_id);
        return;
    }
    console.log(`Incorrect: ${d.author.username}: ${d.content}`);
    //delete the message ---------------------------------------
    const channel = client.channels.cache.get(ENV.bs_counting_id);
    if (channel) {
            logger.log(`Deleting message ${d.id} in channel ${d.channel_id}`, 'deleting_message', ENV.bs_server_id);
            channel.messages.fetch(d.id)
                .then(message => message.delete())
                .catch(err => {
            if (err.code === 10008) {
                logger.log('Tried to delete a message that does not exist.', 'message_not_found', ENV.bs_server_id);
            } else {
                console.error(err);
            }
        });
    }

    //set streak to 0
    currentStreak.set(author, 0);

    //update mapOfShame -------------------------------------------
    if (author !== client.user.id) {
       if (!mapOfShame.has(author)) {
            mapOfShame.set(author, 0);
       }
        mapOfShame.set(author, mapOfShame.get(author) + 1);
        saveStats(); 
        // Dual-write to SQLite users table
        upsertUserStats(author);
    }
    

    //Determine type of incorrect count ---------------------------
    if (lastUser === author) {
        logger.log(`${author} Counted twice in a row! They now have ${mapOfShame.get(author)} incorrect counts.`, 'counted_twice_in_a_row', ENV.bs_server_id);
    } else {
      logger.log(`${author} had a incorrect number, with ${newNumber}! They now have ${mapOfShame.get(author)} incorrect counts.`, 'incorrect_count', ENV.bs_server_id);
    }

    // Log message to SQLite (incorrect)
    const isNumeric = /^-?\d+$/.test(d.content || '');
    const hasLeadingZero = /^-?0\d+/.test(d.content || '');
    const parsedNumber = (typeof newNumber === 'number' && !isNaN(newNumber)) ? newNumber : null;
    const delta = (parsedNumber !== null && typeof lastNumber === 'number') ? (parsedNumber - lastNumber) : null;
    logMessageToSQLite(d, {
        author,
        isNumeric,
        parsedNumber,
        hasLeadingZero,
        numberDelta: delta,
        isCorrect: false
    });
}

function saveStats() {
    logger.log('Saving stats to countingStats.json', 'saving_stats', ENV.bs_server_id);
    try {
        fs.writeFileSync(
            path.join(__dirname, 'data', 'countingStats.json'),
            JSON.stringify({
                shame: Array.from(mapOfShame.entries()),
                fame: Array.from(mapOfFame.entries()),
                currentStreak: Array.from(currentStreak.entries()),
                bestStreak: Array.from(bestStreak.entries()),
                dailyCounts: Array.from(dailyCounts.entries()),
                positiveCounts: Array.from(positiveCounts.entries()),
                negativeCounts: Array.from(negativeCounts.entries())
            }, null, 2)
        );
    } catch (err) {
        logger.log(`Error saving stats: ${err.message}`, 'error_saving_stats', ENV.bs_server_id);
    }
}

function loadStats() {
    // Prefer loading from SQLite users table; fallback to JSON if unavailable
    if (sqlite && sqlite.db) {
        try {
            const rows = sqlite.db.prepare('SELECT user_id, fame, shame, best_streak, current_streak, pos_counts, neg_counts FROM users').all();
            mapOfShame.clear();
            mapOfFame.clear();
            currentStreak.clear();
            bestStreak.clear();
            positiveCounts.clear();
            negativeCounts.clear();
            for (const r of rows) {
                const id = String(r.user_id);
                if (r.shame != null) mapOfShame.set(id, r.shame);
                if (r.fame != null) mapOfFame.set(id, r.fame);
                if (r.current_streak != null) currentStreak.set(id, r.current_streak);
                if (r.best_streak != null) bestStreak.set(id, r.best_streak);
                if (r.pos_counts != null) positiveCounts.set(id, r.pos_counts);
                if (r.neg_counts != null) negativeCounts.set(id, r.neg_counts);
            }
            // dailyCounts remains in-memory and persisted to JSON by saveStats; commands like /graph can read from SQL instead
            return;
        } catch (e) {
            try { logger.log(`SQLite loadStats error: ${e.message}`, 'sqlite_error', ENV.bs_server_id); } catch {}
        }
    }

    // Fallback to prior JSON format
    try {
        const data = fs.readFileSync(path.join(__dirname, 'data', 'countingStats.json'), 'utf8');
        const stats = JSON.parse(data);

        mapOfShame.clear();
        for (const [key, value] of stats.shame || []) {
            mapOfShame.set(key, value);
        }

        mapOfFame.clear();
        for (const [key, value] of stats.fame || []) {
            mapOfFame.set(key, value);
        }

        currentStreak.clear();
        for (const [key, value] of stats.currentStreak || []) {
            currentStreak.set(key, value);
        }

        bestStreak.clear();
        for (const [key, value] of stats.bestStreak || []) {
            bestStreak.set(key, value);
        }

        dailyCounts.clear();
        for (const [key, value] of stats.dailyCounts || []) {
            dailyCounts.set(key, value);
        }

        positiveCounts.clear();
        for (const [key, value] of stats.positiveCounts || []) {
            positiveCounts.set(key, value);
        }

        negativeCounts.clear();
        for (const [key, value] of stats.negativeCounts || []) {
            negativeCounts.set(key, value);
        }
    } catch (err) {
        logger.log('No stats file found, starting fresh.', 'no_stats_file', ENV.bs_server_id);
    }
}

function setLastNumber(num) {
    lastNumber = num;
}
function getLastNumber() {
    return lastNumber;
}
function setLastUser(id) {
    lastUser = id;
}





module.exports = {
    mapOfShame,
    mapOfFame,
    newMessage,
    saveStats,
    loadStats,
    lastNumber,
    setLastNumber,
    getLastNumber,
    setLastUser, 
    currentStreak,
    bestStreak,
    dailyCounts, 
    positiveCounts,
    negativeCounts
};