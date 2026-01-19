package org.gudu0.countingbot.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ConsoleLog {
    private ConsoleLog() {}

    // Flip this to false later if you want to keep INFO/WARN/ERROR only.
    @SuppressWarnings("CanBeFinal")
    public static volatile boolean DEBUG = false;

    private static final String ESC = "\u001B[";

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());

    private static String fmt(String level, String tag, String msg) {
        return "[" + TS.format(Instant.now()) + "] [" + level + "] [" + tag + "] " + msg;
    }

    public static void info(String tag, String msg) {
        System.out.println(fmt("INFO", tag, msg));
    }

    public static void warn(String tag, String msg) {
        System.out.println(fmt(ESC + "93m" + "WARN" + ESC + "0m", tag, msg));
    }

    public static void debug(String tag, String msg) {
        if (!DEBUG) return;
        System.out.println(fmt(ESC + "32m" + "DEBUG" + ESC + "0m", tag, msg));
    }

    public static void error(String tag, String msg) {
        System.err.println(fmt(ESC + "31m" + "ERROR" + ESC + "0m", tag, msg));
    }

    public static void error(String tag, String msg, Throwable t) {
        System.err.println(fmt(ESC + "31m" + "ERROR" + ESC + "0m", tag, msg));
        if (t != null) t.printStackTrace(System.err);
    }
}
