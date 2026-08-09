package sh.variiuz.worldagent.tx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import sh.variiuz.worldagent.WorldAgentPlugin;

@ExtendWith(MockitoExtension.class)
class TransactionManagerTest {

    @Mock
    WorldAgentPlugin plugin;
    @Mock
    FileConfiguration config;

    TransactionManager tx;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(config.getBoolean("transactions.enabled", true)).thenReturn(true);
        lenient().when(config.getInt("transactions.max_blocks", 250_000)).thenReturn(250_000);
        lenient().when(config.getInt("limits.max_blocks_per_request", 250_000)).thenReturn(250_000);
        lenient().when(config.getInt("transactions.max_stack", 30)).thenReturn(30);
        tx = new TransactionManager(plugin);
    }

    @Test
    void commitWithNoRecordsReturnsNull() {
        tx.begin("test");
        assertNull(tx.commit());
        assertEquals(0, tx.undoSize());
        assertFalse(tx.hasOpen());
    }

    @Test
    void recordThenCommitPushesUndoStack() {
        Block block = mockBlock("world", 10, 64, 20, Material.STONE);
        tx.begin("fill");
        tx.record(block);
        String id = tx.commit();
        assertTrue(id != null && !id.isBlank());
        assertEquals(1, tx.undoSize());
        assertFalse(tx.hasOpen());
    }

    @Test
    void abortRestoresRecordedMaterialsAndClearsOpenTx() {
        Block source = mockBlock("world", 5, 70, 5, Material.DIRT);
        World world = source.getWorld();
        Block restoreTarget = mock(Block.class);
        when(world.getBlockAt(5, 70, 5)).thenReturn(restoreTarget);

        tx.begin("batch");
        tx.record(source);
        assertEquals(1, tx.openRecorded());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            tx.abort();
        }

        verify(restoreTarget).setType(eq(Material.DIRT), eq(false));
        assertFalse(tx.hasOpen());
        assertEquals(0, tx.undoSize());
    }

    @Test
    void abortDoesNotTouchCommittedUndoStack() {
        Block first = mockBlock("world", 1, 1, 1, Material.STONE);
        tx.begin("a");
        tx.record(first);
        tx.commit();
        assertEquals(1, tx.undoSize());

        Block second = mockBlock("world", 2, 2, 2, Material.GRASS_BLOCK);
        World world = second.getWorld();
        Block restoreTarget = mock(Block.class);
        when(world.getBlockAt(2, 2, 2)).thenReturn(restoreTarget);

        tx.begin("b");
        tx.record(second);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            tx.abort();
        }

        assertEquals(1, tx.undoSize());
        assertFalse(tx.hasOpen());
    }

    @Test
    void firstTouchOnlyRecordsOnce() {
        Block block = mockBlock("world", 0, 64, 0, Material.STONE);
        tx.begin("edit");
        tx.record(block);
        tx.record(block);
        assertEquals(1, tx.openRecorded());
    }

    private static Block mockBlock(String worldName, int x, int y, int z, Material type) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getType()).thenReturn(type);
        return block;
    }
}
