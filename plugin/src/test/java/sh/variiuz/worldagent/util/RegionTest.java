package sh.variiuz.worldagent.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

class RegionTest {

    @Test
    void normalizesBoundsAndComputesVolume() {
        World world = mock(World.class);
        Region region = new Region(world, 10, 5, 0, 1, 7, 2);
        assertEquals(1, region.minX);
        assertEquals(10, region.maxX);
        assertEquals(5, region.minY);
        assertEquals(7, region.maxY);
        assertEquals(0, region.minZ);
        assertEquals(2, region.maxZ);
        assertEquals(10L * 3L * 3L, region.volume());
        assertEquals(10, region.edgeX());
        assertEquals(3, region.edgeY());
        assertEquals(3, region.edgeZ());
    }
}
