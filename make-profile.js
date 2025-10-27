const { createCanvas, loadImage } = require('canvas');
const fs = require('fs');
const path = require('path');
let sqlite;
try {
    sqlite = require('./db');
} catch (_) {
    sqlite = null;
}

const statsPath = path.join(__dirname, 'data', 'countingStats.json');
const achievementsPath = path.join(__dirname, 'data', 'userAchievements.json');
const definitionsPath = path.join(__dirname, 'data', 'achievementDefinitions.json');


//const userId = "733113260496126053";

function getStat(arr, userId) {
    const entry = arr.find(([id]) => id === userId);
    return entry ? entry[1] : 0;
}

async function makeProfile(userId, username, avatarUrl) {
    const canvas = createCanvas(500, 900);
    const ctx = canvas.getContext('2d');

    // Load achievement definitions (shared for both backends)
    const achievementDefinitions = JSON.parse(fs.readFileSync(definitionsPath, 'utf8'));

    // Load stats and achievements either from SQLite (preferred) or fallback JSON files
    let fame = 0, shame = 0, currentStreakVal = 0, bestStreakVal = 0;
    let earnedAchievementIds = [];

    if (sqlite && sqlite.db) {
        try {
            const row = sqlite.db.prepare('SELECT fame, shame, best_streak, current_streak FROM users WHERE user_id = ?').get(String(userId));
            if (row) {
                fame = row.fame || 0;
                shame = row.shame || 0;
                bestStreakVal = row.best_streak || 0;
                currentStreakVal = row.current_streak || 0;
            }
            const achRows = sqlite.db.prepare('SELECT achievement_id FROM achievements WHERE user_id = ? ORDER BY earned_at').all(String(userId));
            earnedAchievementIds = (achRows || []).map(r => r.achievement_id);
        } catch (e) {
            // Fallback to JSON on any DB error
            const stats = JSON.parse(fs.readFileSync(statsPath, 'utf8'));
            const userAchievements = JSON.parse(fs.readFileSync(achievementsPath, 'utf8'));
            fame = getStat(stats.fame, userId);
            shame = getStat(stats.shame, userId);
            currentStreakVal = getStat(stats.currentStreak, userId);
            bestStreakVal = getStat(stats.bestStreak, userId);
            earnedAchievementIds = userAchievements[userId] || [];
        }
    } else {
        const stats = JSON.parse(fs.readFileSync(statsPath, 'utf8'));
        const userAchievements = JSON.parse(fs.readFileSync(achievementsPath, 'utf8'));
        fame = getStat(stats.fame, userId);
        shame = getStat(stats.shame, userId);
        currentStreakVal = getStat(stats.currentStreak, userId);
        bestStreakVal = getStat(stats.bestStreak, userId);
        earnedAchievementIds = userAchievements[userId] || [];
    }

    // Background
    ctx.fillStyle = '#7289DA';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // Draw title
    ctx.font = 'bold 40px Sans-serif';
    ctx.fillStyle = '#d5d5d5ff';
    ctx.fillText('User Profile', 150, 100);

    // Draw username
    ctx.font = 'bold 32px Sans-serif';
    ctx.fillStyle = '#fff';
    ctx.fillText(`${username}`, 200, 150);

    // Stats 
    let startStatsY = 210;
    let statcount = 0;
    ctx.font = '24px Sans-serif';
    ctx.fillStyle = '#fff';
    ctx.fillText(`Fame: ${fame}`, 20, startStatsY + 30 * statcount); statcount++;
    ctx.fillText(`Shame: ${shame}`, 20, startStatsY + 30 * statcount); statcount++;
    ctx.fillText(`Current Streak: ${currentStreakVal}`, 20, startStatsY + 30 * statcount); statcount++;
    ctx.fillText(`Best Streak: ${bestStreakVal}`, 20, startStatsY + 30 * statcount); statcount++;

    // Achievements
    ctx.font = '28px Sans-serif';
    ctx.fillStyle = '#fff';
    ctx.fillText('Achievements:', 20, 350);

    ctx.font = '20px Sans-serif';
    const userAchieves = earnedAchievementIds;
    let achieveY = 380;
    for (const achieveId of userAchieves) {
        const achieve = achievementDefinitions[achieveId];
        if (achieve) {
            ctx.fillText(`• ${achieve.name}`, 40, achieveY);
            achieveY += 28;
        }
    }

    // Avatar settings
    const avatarX = 20;
    const avatarY = 75;
    const avatarSize = 100;
    const avatarRadius = avatarSize / 2;

    // Load avatar image
    const avatar = await loadImage(avatarUrl);

    // Clip to circle
    ctx.save();
    ctx.beginPath();
    ctx.arc(avatarX + avatarRadius, avatarY + avatarRadius, avatarRadius, 0, Math.PI * 2, true);
    ctx.closePath();
    ctx.clip();

    // Draw avatar (will be clipped)
    ctx.drawImage(avatar, avatarX, avatarY, avatarSize, avatarSize);
    ctx.restore();

    // Draw border circle
    ctx.beginPath();
    ctx.arc(avatarX + avatarRadius, avatarY + avatarRadius, avatarRadius, 0, Math.PI * 2, true);
    ctx.closePath();
    ctx.lineWidth = 2;
    ctx.strokeStyle = '#000';
    ctx.stroke();

    const buffer = canvas.toBuffer('image/png');
    fs.writeFileSync('./profile.png', buffer); 
    return buffer;
}
//makeProfile();

module.exports = { makeProfile };