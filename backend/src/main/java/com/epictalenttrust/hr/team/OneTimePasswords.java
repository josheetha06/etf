package com.epictalenttrust.hr.team;

import java.security.SecureRandom;

/** Readable one-time passwords, for example "Kp7m-Qx3t-Wv9r". No look-alike characters such as 0/O or 1/l. */
public final class OneTimePasswords {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String ALL = UPPER + LOWER + DIGITS;
    private static final SecureRandom RANDOM = new SecureRandom();

    private OneTimePasswords() {
    }

    public static String generate() {
        StringBuilder out = new StringBuilder();
        for (int group = 0; group < 3; group++) {
            if (group > 0) {
                out.append('-');
            }
            out.append(pick(UPPER)).append(pick(LOWER)).append(pick(DIGITS)).append(pick(ALL));
        }
        return out.toString();
    }

    private static char pick(String chars) {
        return chars.charAt(RANDOM.nextInt(chars.length()));
    }
}
