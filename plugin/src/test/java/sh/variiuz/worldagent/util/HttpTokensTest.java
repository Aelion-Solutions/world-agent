package sh.variiuz.worldagent.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HttpTokensTest {

    @Test
    void rejectsPlaceholdersAndShortSecrets() {
        assertTrue(HttpTokens.isUnusable(null));
        assertTrue(HttpTokens.isUnusable(""));
        assertTrue(HttpTokens.isUnusable("change-me-world-agent"));
        assertTrue(HttpTokens.isUnusable("replace-me-with-a-long-random-secret"));
        assertTrue(HttpTokens.isUnusable("short"));
    }

    @Test
    void acceptsGeneratedToken() {
        String token = HttpTokens.generate();
        assertFalse(HttpTokens.isUnusable(token));
        assertTrue(token.length() >= 32);
    }
}
