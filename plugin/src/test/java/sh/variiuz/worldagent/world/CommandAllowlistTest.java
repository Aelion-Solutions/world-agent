package sh.variiuz.worldagent.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CommandAllowlistTest {

    private static final List<String> ALLOW = List.of("tp ", "say ", "time ");

    @Test
    void acceptsAllowlistedPrefixes() {
        assertTrue(WorldAct.isCommandAllowlisted(ALLOW, "tp Steve 0 64 0"));
        assertTrue(WorldAct.isCommandAllowlisted(ALLOW, "SAY hello"));
        assertTrue(WorldAct.isCommandAllowlisted(ALLOW, "time set day"));
    }

    @Test
    void rejectsUnknownCommands() {
        assertFalse(WorldAct.isCommandAllowlisted(ALLOW, "op Steve"));
        assertFalse(WorldAct.isCommandAllowlisted(ALLOW, ""));
        assertFalse(WorldAct.isCommandAllowlisted(ALLOW, null));
        assertFalse(WorldAct.isCommandAllowlisted(ALLOW, "tpa Steve"));
    }
}
