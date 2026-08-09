package sh.variiuz.worldagent.util;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import com.google.gson.JsonObject;

import sh.variiuz.worldagent.api.ApiException;

public final class RegionLimits {

    private RegionLimits() {
    }

    public static Region parseRegion(FileConfiguration config, JsonObject body) {
        String worldName = getString(body, "world", null);
        if (worldName == null) {
            throw new ApiException(400, "Missing world");
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new ApiException(404, "World not found: " + worldName);
        }

        Integer x1 = getInt(body, "x1");
        Integer y1 = getInt(body, "y1");
        Integer z1 = getInt(body, "z1");
        Integer x2 = getInt(body, "x2");
        Integer y2 = getInt(body, "y2");
        Integer z2 = getInt(body, "z2");

        // Also accept center+radius or min/max aliases
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
        long maxVolume = config.getLong("limits.max_volume", 200_000L);
        int maxEdge = config.getInt("limits.max_edge", 128);
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

    public static void requireConfirm(FileConfiguration config, JsonObject body) {
        if (!config.getBoolean("mutations.enabled", true)) {
            throw new ApiException(403, "Mutations disabled");
        }
        if (config.getBoolean("mutations.require_confirm", true)) {
            if (!body.has("confirm") || !body.get("confirm").getAsBoolean()) {
                throw new ApiException(400, "Mutating ops require confirm:true");
            }
        }
    }

    private static String getString(JsonObject o, String key, String def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
    }

    private static Integer getInt(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsInt() : null;
    }
}
