package sh.variiuz.worldagent.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

import sh.variiuz.worldagent.api.ApiException;

/** Shared world / material lookups for API handlers and world ops. */
public final class Worlds {

    private Worlds() {
    }

    public static World requireWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            throw new ApiException(400, "Missing world");
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new ApiException(404, "World not found: " + worldName);
        }
        return world;
    }

    public static Material requireBlockMaterial(String materialName) {
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isBlock()) {
            throw new ApiException(400, "Invalid block material: " + materialName);
        }
        return material;
    }
}
