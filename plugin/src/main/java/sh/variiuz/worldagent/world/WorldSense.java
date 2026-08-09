package sh.variiuz.worldagent.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import sh.variiuz.worldagent.api.ApiException;
import sh.variiuz.worldagent.api.Json;
import sh.variiuz.worldagent.util.Region;
import sh.variiuz.worldagent.util.Worlds;

public final class WorldSense {

    private WorldSense() {
    }

    public static JsonObject players() {
        JsonArray arr = new JsonArray();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location loc = player.getLocation();
            JsonObject entry = Json.obj();
            entry.addProperty("name", player.getName());
            entry.addProperty("uuid", player.getUniqueId().toString());
            entry.addProperty("world", loc.getWorld() != null ? loc.getWorld().getName() : "");
            entry.addProperty("x", loc.getX());
            entry.addProperty("y", loc.getY());
            entry.addProperty("z", loc.getZ());
            entry.addProperty("yaw", loc.getYaw());
            entry.addProperty("pitch", loc.getPitch());
            entry.addProperty("block_x", loc.getBlockX());
            entry.addProperty("block_y", loc.getBlockY());
            entry.addProperty("block_z", loc.getBlockZ());
            entry.addProperty("gamemode", player.getGameMode().name());
            arr.add(entry);
        }
        JsonObject out = Json.obj();
        out.addProperty("count", arr.size());
        out.add("players", arr);
        return out;
    }

    public static JsonObject getBlock(String worldName, int x, int y, int z) {
        World world = Worlds.requireWorld(worldName);
        Material mat = world.getBlockAt(x, y, z).getType();
        JsonObject result = Json.obj();
        result.addProperty("world", worldName);
        result.addProperty("x", x);
        result.addProperty("y", y);
        result.addProperty("z", z);
        result.addProperty("material", mat.getKey().toString());
        result.addProperty("is_air", mat.isAir());
        result.addProperty("is_solid", mat.isSolid());
        return result;
    }

    public static JsonObject heightmap(String worldName, int x1, int z1, int x2, int z2, int yFrom, int yTo) {
        World world = Worlds.requireWorld(worldName);
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        int minY = Math.max(world.getMinHeight(), Math.min(yFrom, yTo));
        int maxY = Math.min(world.getMaxHeight() - 1, Math.max(yFrom, yTo));

        long cells = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        if (cells > 16_384) {
            throw new ApiException(400, "heightmap too large (max 16384 columns)");
        }

        JsonArray rows = new JsonArray();
        int highest = Integer.MIN_VALUE;
        int lowest = Integer.MAX_VALUE;
        for (int z = minZ; z <= maxZ; z++) {
            JsonArray row = new JsonArray();
            for (int x = minX; x <= maxX; x++) {
                int top = minY - 1;
                for (int y = maxY; y >= minY; y--) {
                    if (!world.getBlockAt(x, y, z).getType().isAir()) {
                        top = y;
                        break;
                    }
                }
                row.add(top);
                if (top >= minY) {
                    highest = Math.max(highest, top);
                    lowest = Math.min(lowest, top);
                }
            }
            rows.add(row);
        }

        JsonObject result = Json.obj();
        result.addProperty("world", worldName);
        result.addProperty("min_x", minX);
        result.addProperty("min_z", minZ);
        result.addProperty("max_x", maxX);
        result.addProperty("max_z", maxZ);
        result.addProperty("y_from", minY);
        result.addProperty("y_to", maxY);
        result.addProperty("note", "grid[z][x] = highest non-air Y, or y_from-1 if empty");
        if (highest != Integer.MIN_VALUE) {
            result.addProperty("highest", highest);
            result.addProperty("lowest", lowest);
        }
        result.add("grid", rows);
        return result;
    }

    public static JsonObject health() {
        JsonObject o = Json.obj();
        o.addProperty("ok", true);
        o.addProperty("plugin", "AelionWorldAgent");
        o.addProperty("bukkit", Bukkit.getVersion());
        o.addProperty("online_players", Bukkit.getOnlinePlayers().size());
        o.addProperty("worlds", Bukkit.getWorlds().size());
        return o;
    }

    public static JsonArray worlds() {
        JsonArray arr = new JsonArray();
        for (World world : Bukkit.getWorlds()) {
            JsonObject o = Json.obj();
            o.addProperty("name", world.getName());
            o.addProperty("env", world.getEnvironment().name());
            Location spawn = world.getSpawnLocation();
            o.addProperty("spawn_x", spawn.getBlockX());
            o.addProperty("spawn_y", spawn.getBlockY());
            o.addProperty("spawn_z", spawn.getBlockZ());
            o.addProperty("min_height", world.getMinHeight());
            o.addProperty("max_height", world.getMaxHeight());
            arr.add(o);
        }
        return arr;
    }

    public static JsonObject scan(Region region, boolean detailBlocks, int detailLimit) {
        Map<String, Integer> counts = new HashMap<>();
        int solid = 0;
        int air = 0;
        Integer minSolidY = null;
        Integer maxSolidY = null;
        JsonArray blocks = new JsonArray();

        for (int x = region.minX; x <= region.maxX; x++) {
            for (int y = region.minY; y <= region.maxY; y++) {
                for (int z = region.minZ; z <= region.maxZ; z++) {
                    Block block = region.world.getBlockAt(x, y, z);
                    Material mat = block.getType();
                    String key = mat.getKey().toString();
                    counts.merge(key, 1, Integer::sum);
                    if (mat.isAir()) {
                        air++;
                    } else {
                        solid++;
                        minSolidY = minSolidY == null ? y : Math.min(minSolidY, y);
                        maxSolidY = maxSolidY == null ? y : Math.max(maxSolidY, y);
                        if (detailBlocks && blocks.size() < detailLimit) {
                            JsonObject b = Json.obj();
                            b.addProperty("x", x);
                            b.addProperty("y", y);
                            b.addProperty("z", z);
                            b.addProperty("material", key);
                            blocks.add(b);
                        }
                    }
                }
            }
        }

        List<Map.Entry<String, Integer>> top = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(12)
                .toList();

        JsonObject dominant = Json.obj();
        for (Map.Entry<String, Integer> e : top) {
            dominant.addProperty(e.getKey(), e.getValue());
        }

        JsonObject o = Json.obj();
        o.addProperty("world", region.world.getName());
        o.addProperty("min_x", region.minX);
        o.addProperty("min_y", region.minY);
        o.addProperty("min_z", region.minZ);
        o.addProperty("max_x", region.maxX);
        o.addProperty("max_y", region.maxY);
        o.addProperty("max_z", region.maxZ);
        o.addProperty("volume", region.volume());
        o.addProperty("solid", solid);
        o.addProperty("air", air);
        o.addProperty("empty", solid == 0);
        if (minSolidY != null) {
            o.addProperty("min_solid_y", minSolidY);
            o.addProperty("max_solid_y", maxSolidY);
        }
        o.add("dominant_materials", dominant);
        if (detailBlocks) {
            o.add("blocks", blocks);
        }
        return o;
    }

    public static JsonObject slice(Region region) {
        // Top-down: for each x,z take highest non-air in y range
        JsonArray rows = new JsonArray();
        Map<String, Integer> legendCounts = new HashMap<>();

        for (int z = region.minZ; z <= region.maxZ; z++) {
            JsonArray row = new JsonArray();
            for (int x = region.minX; x <= region.maxX; x++) {
                String mat = "minecraft:air";
                for (int y = region.maxY; y >= region.minY; y--) {
                    Material m = region.world.getBlockAt(x, y, z).getType();
                    if (!m.isAir()) {
                        mat = m.getKey().toString();
                        break;
                    }
                }
                row.add(shortName(mat));
                legendCounts.merge(mat, 1, Integer::sum);
            }
            rows.add(row);
        }

        JsonObject legend = Json.obj();
        legendCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .forEach(e -> legend.addProperty(shortName(e.getKey()), e.getKey()));

        JsonObject o = Json.obj();
        o.addProperty("world", region.world.getName());
        o.addProperty("min_x", region.minX);
        o.addProperty("min_z", region.minZ);
        o.addProperty("max_x", region.maxX);
        o.addProperty("max_z", region.maxZ);
        o.addProperty("y_from", region.minY);
        o.addProperty("y_to", region.maxY);
        o.addProperty("note", "grid[z][x] = short material at highest non-air in y range");
        o.add("legend", legend);
        o.add("grid", rows);
        return o;
    }

    public static JsonObject entities(World world, double x, double y, double z, double radius, int max) {
        Location center = new Location(world, x, y, z);
        List<Entity> found = new ArrayList<>(world.getNearbyEntities(center, radius, radius, radius));
        found.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(center)));

        JsonArray arr = new JsonArray();
        int n = 0;
        for (Entity entity : found) {
            if (n >= max) {
                break;
            }
            JsonObject o = Json.obj();
            o.addProperty("uuid", entity.getUniqueId().toString());
            o.addProperty("type", entity.getType().name());
            o.addProperty("name", entity.getName());
            Location loc = entity.getLocation();
            o.addProperty("x", loc.getX());
            o.addProperty("y", loc.getY());
            o.addProperty("z", loc.getZ());
            o.addProperty("is_player", entity instanceof Player);
            arr.add(o);
            n++;
        }

        JsonObject out = Json.obj();
        out.addProperty("world", world.getName());
        out.addProperty("x", x);
        out.addProperty("y", y);
        out.addProperty("z", z);
        out.addProperty("radius", radius);
        out.addProperty("count", arr.size());
        out.addProperty("truncated", found.size() > max);
        out.add("entities", arr);
        return out;
    }

    private static String shortName(String key) {
        if (key == null) {
            return "?";
        }
        int idx = key.indexOf(':');
        String name = idx >= 0 ? key.substring(idx + 1) : key;
        return name.toLowerCase(Locale.ROOT);
    }
}
