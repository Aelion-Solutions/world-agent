package sh.variiuz.worldagent.snapshot;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class SnapshotStoreTest {

    @Test
    void evictsOldestWhenOverCapacity() {
        SnapshotStore store = new SnapshotStore(2);
        RegionSnapshot a = new RegionSnapshot("w", 0, 0, 0, 0, 0, 0, Map.of(0L, Material.STONE));
        RegionSnapshot b = new RegionSnapshot("w", 0, 0, 0, 0, 0, 0, Map.of(0L, Material.DIRT));
        RegionSnapshot c = new RegionSnapshot("w", 0, 0, 0, 0, 0, 0, Map.of(0L, Material.AIR));
        String idA = store.put(a);
        String idB = store.put(b);
        String idC = store.put(c);
        assertNull(store.get(idA));
        assertNotNull(store.get(idB));
        assertNotNull(store.get(idC));
    }
}
