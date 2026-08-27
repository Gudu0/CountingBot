package org.gudu0.countingbot.counting;

import net.dv8tion.jda.api.entities.Message;

public final class CountVerifier {
    private CountVerifier() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

//    public static boolean isCorrectNumber(Message message) {
//
//        return false;
//    }

    /**
     * Strict integer parse (matches your rules):
     * - entire content must be digits ONLY, with optional comma thousands-separators in valid positions
     * - no negatives
     * - no leading zeros unless "0"
     */
    public static Parsed parseStrictCount(Message msg) {
        String s = msg.getContentRaw(); // DO NOT trim; whitespace is invalid
        if (s == null || s.isEmpty()) return null;

        int charsSinceComma = 0;
        boolean sawComma = false;
        for (int i = s.length() - 1; i >= 0; i--) {
            char currentCharacter = s.charAt(i);
            if (currentCharacter >= '0' && currentCharacter <= '9') {
                charsSinceComma++;
            } else if (currentCharacter == ',') {
                sawComma = true;
                if (charsSinceComma == 3) {
                    charsSinceComma = 0;
                } else {
                    return null;
                }
            } else {
                return null; //anything not a number or comma is invalid.
            }
        }
        // Leftmost group: 1-3 digits if commas were used, any length (>=1) if not.
        if (charsSinceComma < 1 || (sawComma && charsSinceComma > 3)) {
            return null;
        }
        if (s.charAt(0) == '0' && s.length() > 1) {
            return null; // no leading zeros unless the whole content is "0"
        }

        try {
            long n = Long.parseLong(s.replace(",", ""));
            return new Parsed(n, msg.getAuthor().getIdLong());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record Parsed(long number, long authorId) {}
}
