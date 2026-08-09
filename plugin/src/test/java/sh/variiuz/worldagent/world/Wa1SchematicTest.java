package sh.variiuz.worldagent.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import sh.variiuz.worldagent.api.ApiException;
import sh.variiuz.worldagent.world.WorldAct.Wa1Schematic;

class Wa1SchematicTest {

    @Test
    void parsesHeaderAndEntries() {
        Wa1Schematic schematic = Wa1Schematic.parse(List.of(
                "WA1",
                "world|3|2|4",
                "0,0,0,minecraft:stone",
                "# comment",
                "1,0,0,minecraft:dirt"));
        assertEquals(3, schematic.dx());
        assertEquals(2, schematic.dy());
        assertEquals(4, schematic.dz());
        assertEquals(2, schematic.entries().size());
        assertEquals("minecraft:stone", schematic.entries().get(0).materialName());
        assertEquals("minecraft:dirt", schematic.entries().get(1).materialName());
        assertEquals(0, schematic.entries().get(0).x());
        assertEquals(1, schematic.entries().get(1).x());
    }

    @Test
    void rejectsBadFormat() {
        assertThrows(ApiException.class, () -> Wa1Schematic.parse(List.of("NOTWA1")));
        assertThrows(ApiException.class, () -> Wa1Schematic.parse(List.of(
                "WA1",
                "world|1|1|1",
                "0,0,stone")));
        assertThrows(ApiException.class, () -> Wa1Schematic.parse(List.of(
                "WA1",
                "world|1|1|1",
                "x,0,0,minecraft:stone")));
    }
}
