package org.gudu0.countingbot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.gudu0.countingbot.achievements.AchievementsState;
import org.gudu0.countingbot.achievements.UserAchievements;
import org.gudu0.countingbot.counting.CountingState;
import org.gudu0.countingbot.goals.GoalState;
import org.gudu0.countingbot.stats.StatsData;
import org.gudu0.countingbot.stats.UserStats;
import org.gudu0.countingbot.suggestions.SuggestionEntry;
import org.gudu0.countingbot.suggestions.SuggestionsState;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class MigrateFromSqlite {

    public static void main(@NotNull String[] args) throws Exception {
//        if (args.length < 2) {
//            System.out.println("Usage: MigrateFromSqlite <path-to-counting.db> <output-data-dir>");
//            System.out.println("Example: MigrateFromSqlite ./counting.db ./data");
//            return;
//        }


        Path dbPath = Path.of("C:\\Users\\bobha\\Downloads\\e\\counting.db");
        Path outDir = Path.of("C:\\Users\\bobha\\OneDrive\\Desktop\\projects\\JavaCountingBot\\data");

        ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
            // ---- Load KV ----
            Map<String, String> kv = tableExists(c, "kv") ? loadKv(c) : new HashMap<>();

            // ---- state.json ----
            CountingState state = new CountingState();
            state.lastNumber = parseLongOrDefault(firstNonNull(kv.get("last_number"), kv.get("lastNumber")), -1);
            state.lastUserId = parseLongOrDefault(firstNonNull(kv.get("last_user"), kv.get("lastUser")), 0);
            state.lastMessageId = 0;             // not migrating messages table
            state.globalStreakCurrent = 0;       // not present in sqlite
            state.globalStreakBest = 0;          // not present in sqlite
            // state.userLastValidCountAt stays empty

            // ---- stats.json ----
            StatsData stats = new StatsData();
            if (tableExists(c, "users")) {
                migrateUsers(c, stats);
            }

            // ---- achievements.json ----
            AchievementsState achievements = new AchievementsState();
            if (tableExists(c, "achievements")) {
                migrateAchievements(c, achievements);
            }

            // ---- goals.json ----
            GoalState goal = new GoalState();
            if (tableExists(c, "goals")) {
                migrateGoal(c, goal);
            } else {
                goal.active = false;
            }

            // ---- suggestions.json ----
            SuggestionsState suggestions = new SuggestionsState();
            if (tableExists(c, "suggestions")) {
                migrateSuggestions(c, suggestions);
            }

            // ---- Write files ----
            Files.createDirectories(outDir);
            om.writeValue(outDir.resolve("state.json").toFile(), state);
            om.writeValue(outDir.resolve("stats.json").toFile(), stats);
            om.writeValue(outDir.resolve("achievements.json").toFile(), achievements);
            om.writeValue(outDir.resolve("goals.json").toFile(), goal);
            om.writeValue(outDir.resolve("suggestions.json").toFile(), suggestions);

            System.out.println("✅ Migration complete.");
            System.out.println("State:        " + outDir.resolve("state.json"));
            System.out.println("Stats:        " + outDir.resolve("stats.json"));
            System.out.println("Achievements: " + outDir.resolve("achievements.json"));
            System.out.println("Goals:        " + outDir.resolve("goals.json"));
            System.out.println("Suggestions:  " + outDir.resolve("suggestions.json"));
            System.out.println();
            System.out.println("Note:");
            System.out.println("- lastMessageId is set to 0 (messages table ignored). Bot will resync on startup.");
            System.out.println("- goal deadlines are only migrated if they are ISO-8601 (Instant.parse).");
        }
    }

    // ---------------------------
    // KV
    // ---------------------------

    private static Map<String, String> loadKv(Connection c) throws SQLException {
        Map<String, String> out = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT key, value FROM kv");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getString(2));
            }
        }
        return out;
    }

    // ---------------------------
    // USERS -> stats.json
    // ---------------------------

    private static void migrateUsers(Connection c, StatsData stats) throws SQLException {
        // Expected columns (current db): user_id TEXT, fame INT, shame INT, best_streak INT, current_streak INT, pos_counts INT, neg_counts INT, updated_at INT
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT user_id, fame, shame, best_streak, current_streak, pos_counts, neg_counts FROM users"
        );
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                long userId = parseLongOrDefault(rs.getString("user_id"), 0);
                if (userId == 0) continue;

                UserStats s = stats.getOrCreate(userId);
                s.correct = rs.getLong("fame");
                s.incorrect = rs.getLong("shame");
                s.bestStreak = rs.getLong("best_streak");
                s.currentStreak = rs.getLong("current_streak");
                s.posCounts = rs.getLong("pos_counts");
                s.negCounts = rs.getLong("neg_counts");

                // These don’t exist in sqlite; leave at 0
                // s.lastCorrectAtMs = 0;
                // s.lastIncorrectAtMs = 0;
            }
        }
    }

    // ---------------------------
    // ACHIEVEMENTS -> achievements.json
    // ---------------------------

    private static void migrateAchievements(Connection c, AchievementsState out) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT user_id, achievement_id, earned_at FROM achievements"
        );
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                long userId = parseLongOrDefault(rs.getString("user_id"), 0);
                String achievementId = rs.getString("achievement_id");
                long earnedAt = rs.getLong("earned_at");

                if (userId == 0) continue;
                if (achievementId == null || achievementId.isBlank()) continue;
                if (earnedAt <= 0) earnedAt = System.currentTimeMillis();

                UserAchievements ua = out.getOrCreate(userId);
                ua.unlock(achievementId, earnedAt);
            }
        }
    }

    // ---------------------------
    // GOALS -> goals.json
    // ---------------------------

    private static void migrateGoal(Connection c, GoalState goal) throws SQLException {
        // We migrate ONE goal: the most recent row with created_at present and not completed.
        // If multiple “active” goals exist, we take the newest created_at.

        GoalRow active = queryOneGoal(c,
                "SELECT pinned_message_id, target, created_at, set_by, deadline " +
                        "FROM goals " +
                        "WHERE created_at IS NOT NULL AND (completed_at IS NULL OR completed_at = 0) " +
                        "ORDER BY created_at DESC LIMIT 1"
        );

        if (active != null) {
            goal.goalMessageId = parseLongOrDefault(active.pinnedMessageId, 0);
            goal.active = true;
            goal.target = active.target;
            goal.setByUserId = parseLongOrDefault(active.setBy, 0);
            goal.setAtMillis = active.createdAtMillis;
            goal.deadlineAtMillis = parseDeadlineMillisOrNull(active.deadline);
            goal.lastRenderedNumber = Long.MIN_VALUE;
            return;
        }

        // No active goal found: keep message id if we can, but mark inactive.
        GoalRow latestAny = queryOneGoal(c,
                "SELECT pinned_message_id, target, created_at, set_by, deadline " +
                        "FROM goals " +
                        "WHERE pinned_message_id IS NOT NULL " +
                        "ORDER BY COALESCE(created_at, 0) DESC LIMIT 1"
        );

        if (latestAny != null) {
            goal.goalMessageId = parseLongOrDefault(latestAny.pinnedMessageId, 0);
        }
        goal.active = false;
        goal.target = 0;
        goal.setByUserId = 0;
        goal.setAtMillis = 0;
        goal.deadlineAtMillis = null;
        goal.lastRenderedNumber = Long.MIN_VALUE;
    }

    private static GoalRow queryOneGoal(Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return null;

            String pinned = rs.getString("pinned_message_id");
            long target = rs.getLong("target");
            long created = rs.getLong("created_at");
            String setBy = rs.getString("set_by");
            String deadline = rs.getString("deadline");

            return new GoalRow(pinned, target, created, setBy, deadline);
        }
    }

    private record GoalRow(String pinnedMessageId, long target, long createdAtMillis, String setBy, String deadline) {}

    private static Long parseDeadlineMillisOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;

        // Only migrate ISO-8601 instants cleanly (example: 2026-01-15T05:55:40.259Z)
        try {
            return Instant.parse(s).toEpochMilli();
        } catch (Exception ignored) {
            return null;
        }
    }

    // ---------------------------
    // SUGGESTIONS -> suggestions.json
    // ---------------------------

    private static void migrateSuggestions(Connection c, SuggestionsState out) throws SQLException {
        // Old schema: id TEXT, user_id TEXT, text TEXT, status TEXT, created_at INTEGER
        // New schema: sequential long ids in SuggestionsState + SuggestionEntry list.
        long nextId = 1;

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT user_id, text, created_at FROM suggestions ORDER BY COALESCE(created_at, 0) ASC"
        );
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                long authorId = parseLongOrDefault(rs.getString("user_id"), 0);
                String text = rs.getString("text");
                long createdAt = rs.getLong("created_at");

                if (text == null) text = "";
                if (createdAt <= 0) createdAt = System.currentTimeMillis();

                // guildId and authorTag don't exist in sqlite suggestions table:
                long guildId = 0;
                String authorTag = null;

                SuggestionEntry e = new SuggestionEntry(nextId, guildId, authorId, authorTag, text, createdAt);
                out.suggestions.add(e);
                nextId++;
            }
        }

        out.nextId = nextId;
    }

    // ---------------------------
    // Helpers
    // ---------------------------

    private static boolean tableExists(Connection c, String tableName) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?"
        )) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String firstNonNull(String a, String b) {
        return (a != null) ? a : b;
    }

    private static long parseLongOrDefault(String s, long def) {
        if (s == null || s.isBlank()) return def;
        try { return Long.parseLong(s.trim()); }
        catch (Exception ignored) { return def; }
    }
}
