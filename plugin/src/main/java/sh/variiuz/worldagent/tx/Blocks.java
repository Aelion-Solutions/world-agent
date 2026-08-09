package sh.variiuz.worldagent.tx;

import org.bukkit.Material;
import org.bukkit.block.Block;

import sh.variiuz.worldagent.WorldAgentPlugin;

/**
 * All mutating block writes should go through here so transactions can record undo data.
 */
public final class Blocks {

    private static volatile WorldAgentPlugin plugin;

    private Blocks() {
    }

    public static void init(WorldAgentPlugin pluginInstance) {
        plugin = pluginInstance;
    }

    public static void set(Block block, Material material) {
        if (block.getType() == material) {
            return;
        }
        TransactionManager tx = plugin != null ? plugin.getTransactions() : null;
        if (tx != null) {
            tx.record(block);
        }
        block.setType(material, false);
    }
}
