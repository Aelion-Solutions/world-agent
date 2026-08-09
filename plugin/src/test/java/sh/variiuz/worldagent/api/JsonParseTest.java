package sh.variiuz.worldagent.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JsonParseTest {

    @Test
    void parseObjectHandlesBlankAndNonObject() {
        assertTrue(Json.parseObject(null).entrySet().isEmpty());
        assertTrue(Json.parseObject("").entrySet().isEmpty());
        assertTrue(Json.parseObject("[1,2]").entrySet().isEmpty());
    }

    @Test
    void parseObjectReadsFields() {
        var body = Json.parseObject("{\"world\":\"overworld\",\"x\":1}");
        assertEquals("overworld", body.get("world").getAsString());
        assertEquals(1, body.get("x").getAsInt());
        assertFalse(body.has("confirm"));
    }
}
