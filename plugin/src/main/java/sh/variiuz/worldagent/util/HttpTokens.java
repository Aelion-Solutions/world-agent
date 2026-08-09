package sh.variiuz.worldagent.util;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/** Bearer-token helpers: refuse known placeholders and generate strong secrets. */
public final class HttpTokens {

    private static final Set<String> DENYLIST = Set.of(
            "change-me-world-agent",
            "replace-me-with-a-long-random-secret",
            "changeme",
            "change-me");

    private static final SecureRandom RANDOM = new SecureRandom();

    private HttpTokens() {
    }

    public static boolean isUnusable(String token) {
        if (token == null || token.isBlank()) {
            return true;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        return DENYLIST.contains(normalized) || token.trim().length() < 16;
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
