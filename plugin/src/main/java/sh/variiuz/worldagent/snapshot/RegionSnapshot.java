package sh.variiuz.worldagent.snapshot;

import java.util.Map;

import org.bukkit.Material;

public record RegionSnapshot(
        String world,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        Map<Long, Material> blocks
) {
}
