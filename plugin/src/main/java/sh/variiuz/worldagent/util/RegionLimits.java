package sh.variiuz.worldagent.util;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import com.google.gson.JsonObject;

import sh.variiuz.worldagent.api.ApiException;

public final class RegionLimits {

    public static final long DEFAULT_MAX_VOLUME = 500_000L;
    public static final int DEFAULT_MAX_EDGE = 160;
    public static final int DEFAULT_MAX_BLOCKS_PER_REQUEST = 250_000;
    public static final int DEFAULT_MAX_BATCH_OPS = 64;

    private RegionLimits() {
    }

    public static Region parseRegion(FileConfiguration config, JsonObject body) {
        String worldName = getString(body, "world", null);
        World world = Worlds.requireWorld(worldName);

        Integer x1 = getInt(body, "x1");
        Integer y1 = getInt(body, "y1");
        Integer z1 = getInt(body, "z1");
        Integer x2 = getInt(body, "x2");
        Integer y2 = getInt(body, "y2");
        Integer z2 = getInt(body, "z2");

        if (x1 == null && body.has("x") && body.has("radius")) {
            int cx = body.get("x").getAsInt();
            int cy = body.has("y") ? body.get("y").getAsInt() : 64;
            int cz = body.get("z").getAsInt();
            int r = body.get("radius").getAsInt();
            int ry = body.has("radius_y") ? body.get("radius_y").getAsInt() : r;
            x1 = cx - r;
            y1 = cy - ry;
            z1 = cz - r;
            x2 = cx + r;
            y2 = cy + ry;
            z2 = cz + r;
        }

        if (x1 == null || y1 == null || z1 == null || x2 == null || y2 == null || z2 == null) {
            throw new ApiException(400, "Need x1,y1,z1,x2,y2,z2 or x,y,z,radius");
        }

        Region region = new Region(world, x1, y1, z1, x2, y2, z2);
        validate(config, region);
        return region;
    }

    public static Region parseQueryRegion(FileConfiguration config, java.util.Map<String, String> q) {
        JsonObject body = new JsonObject();
        q.forEach((k, v) -> {
            try {
                if (v.matches("-?\\d+")) {
                    body.addProperty(k, Integer.parseInt(v));
                } else {
                    body.addProperty(k, v);
                }
            } catch (Exception e) {
                body.addProperty(k, v);
            }
        });
        return parseRegion(config, body);
    }

    public static void validate(FileConfiguration config, Region region) {
        long maxVolume = config.getLong("limits.max_volume", DEFAULT_MAX_VOLUME);
        int maxEdge = config.getInt("limits.max_edge", DEFAULT_MAX_EDGE);
        if (region.volume() > maxVolume) {
            throw new ApiException(400, "Region volume " + region.volume() + " exceeds max_volume " + maxVolume);
        }
        if (region.edgeX() > maxEdge || region.edgeY() > maxEdge || region.edgeZ() > maxEdge) {
            throw new ApiException(400, "Region edge exceeds max_edge " + maxEdge);
        }
        int minH = region.world.getMinHeight();
        int maxH = region.world.getMaxHeight() - 1;
        if (region.minY < minH || region.maxY > maxH) {
            throw new ApiException(400, "Y out of world bounds [" + minH + "," + maxH + "]");
        }
    }

    /** Validates an AABB that may not yet be a Region (e.g. before world height checks). */
    public static void validateBounds(FileConfiguration config, World world, int x1, int y1, int z1,
            int x2, int y2, int z2) {
        validate(config, new Region(world, x1, y1, z1, x2, y2, z2));
    }

    public static void ensureRequestBudget(FileConfiguration config, long additionalBlocks) {
        int max = config.getInt("limits.max_blocks_per_request", DEFAULT_MAX_BLOCKS_PER_REQUEST);
        if (additionalBlocks > max) {
            throw new ApiException(400,
                    "Request would touch " + additionalBlocks + " blocks; max_blocks_per_request is " + max);
        }
    }

    public static void requireMutationsEnabled(FileConfiguration config) {
        if (!config.getBoolean("mutations.enabled", true)) {
            throw new ApiException(403, "Mutations disabled");
        }
    }

    public static int maxBatchOps(FileConfiguration config) {
        return config.getInt("limits.max_batch_ops", DEFAULT_MAX_BATCH_OPS);
    }

    private static String getString(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    private static Integer getInt(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : null;
    }
}
