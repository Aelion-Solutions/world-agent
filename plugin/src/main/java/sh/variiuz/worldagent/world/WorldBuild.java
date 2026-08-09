package sh.variiuz.worldagent.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import sh.variiuz.worldagent.api.ApiException;
import sh.variiuz.worldagent.api.Json;
import sh.variiuz.worldagent.tx.Blocks;
import sh.variiuz.worldagent.util.Region;
import sh.variiuz.worldagent.util.RegionLimits;

/**
 * Higher-level build primitives for agents.
 */
public final class WorldBuild {

    private WorldBuild() {
    }

    public static JsonObject players() {
        JsonArray arr = new JsonArray();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location loc = player.getLocation();
            JsonObject o = Json.obj();
            o.addProperty("name", player.getName());
            o.addProperty("uuid", player.getUniqueId().toString());
            o.addProperty("world", loc.getWorld() != null ? loc.getWorld().getName() : "");
            o.addProperty("x", loc.getX());
            o.addProperty("y", loc.getY());
            o.addProperty("z", loc.getZ());
            o.addProperty("yaw", loc.getYaw());
            o.addProperty("pitch", loc.getPitch());
            o.addProperty("block_x", loc.getBlockX());
            o.addProperty("block_y", loc.getBlockY());
            o.addProperty("block_z", loc.getBlockZ());
            o.addProperty("gamemode", player.getGameMode().name());
            arr.add(o);
        }
        JsonObject out = Json.obj();
        out.addProperty("count", arr.size());
        out.add("players", arr);
        return out;
    }

    public static JsonObject getBlock(String worldName, int x, int y, int z) {
        World world = requireWorld(worldName);
        Material mat = world.getBlockAt(x, y, z).getType();
        JsonObject o = Json.obj();
        o.addProperty("world", worldName);
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        o.addProperty("material", mat.getKey().toString());
        o.addProperty("is_air", mat.isAir());
        o.addProperty("is_solid", mat.isSolid());
        return o;
    }

    public static JsonObject heightmap(String worldName, int x1, int z1, int x2, int z2, int yFrom, int yTo) {
        World world = requireWorld(worldName);
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

        JsonObject o = Json.obj();
        o.addProperty("world", worldName);
        o.addProperty("min_x", minX);
        o.addProperty("min_z", minZ);
        o.addProperty("max_x", maxX);
        o.addProperty("max_z", maxZ);
        o.addProperty("y_from", minY);
        o.addProperty("y_to", maxY);
        o.addProperty("note", "grid[z][x] = highest non-air Y, or y_from-1 if empty");
        if (highest != Integer.MIN_VALUE) {
            o.addProperty("highest", highest);
            o.addProperty("lowest", lowest);
        }
        o.add("grid", rows);
        return o;
    }

    /**
     * mode: solid | hollow | walls | frame
     */
    public static JsonObject box(Region region, String materialName, String mode) {
        Material mat = requireBlock(materialName);
        String m = mode == null ? "hollow" : mode.toLowerCase(Locale.ROOT);
        int changed = 0;

        for (int x = region.minX; x <= region.maxX; x++) {
            for (int y = region.minY; y <= region.maxY; y++) {
                for (int z = region.minZ; z <= region.maxZ; z++) {
                    boolean onX = x == region.minX || x == region.maxX;
                    boolean onY = y == region.minY || y == region.maxY;
                    boolean onZ = z == region.minZ || z == region.maxZ;
                    boolean place = switch (m) {
                        case "solid" -> true;
                        case "walls" -> onX || onZ; // no floor/roof
                        case "frame" -> (onX && onY) || (onX && onZ) || (onY && onZ);
                        case "hollow" -> onX || onY || onZ;
                        default -> throw new ApiException(400, "mode must be solid|hollow|walls|frame");
                    };
                    if (place) {
                        Blocks.set(region.world.getBlockAt(x, y, z), mat);
                        changed++;
                    }
                }
            }
        }

        JsonObject o = Json.obj();
        o.addProperty("ok", true);
        o.addProperty("mode", m);
        o.addProperty("changed", changed);
        o.addProperty("material", mat.getKey().toString());
        o.addProperty("volume", region.volume());
        return o;
    }

    public static JsonObject line(String worldName, int x1, int y1, int z1, int x2, int y2, int z2,
            String materialName) {
        World world = requireWorld(worldName);
        Material mat = requireBlock(materialName);

        int dx = x2 - x1;
        int dy = y2 - y1;
        int dz = z2 - z1;
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        if (steps > 512) {
            throw new ApiException(400, "line too long (max 512)");
        }
        if (steps == 0) {
            Blocks.set(world.getBlockAt(x1, y1, z1), mat);
            JsonObject o = Json.obj();
            o.addProperty("ok", true);
            o.addProperty("changed", 1);
            return o;
        }

        int changed = 0;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (dx * i) / steps;
            int y = y1 + (dy * i) / steps;
            int z = z1 + (dz * i) / steps;
            Blocks.set(world.getBlockAt(x, y, z), mat);
            changed++;
        }
        JsonObject o = Json.obj();
        o.addProperty("ok", true);
        o.addProperty("changed", changed);
        o.addProperty("material", mat.getKey().toString());
        return o;
    }

    public static JsonObject cylinder(String worldName, int cx, int cy, int cz, int radius, int height,
            String materialName, boolean hollow) {
        World world = requireWorld(worldName);
        Material mat = requireBlock(materialName);
        if (radius < 0 || radius > 64 || height < 1 || height > 128) {
            throw new ApiException(400, "radius 0-64, height 1-128");
        }
        int changed = 0;
        int r2 = radius * radius;
        int inner = Math.max(0, radius - 1);
        int inner2 = inner * inner;
        for (int y = cy; y < cy + height; y++) {
            for (int x = cx - radius; x <= cx + radius; x++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    int d2 = (x - cx) * (x - cx) + (z - cz) * (z - cz);
                    boolean in = d2 <= r2;
                    boolean place = hollow ? (in && d2 >= inner2) : in;
                    if (place) {
                        Blocks.set(world.getBlockAt(x, y, z), mat);
                        changed++;
                    }
                }
            }
        }
        JsonObject o = Json.obj();
        o.addProperty("ok", true);
        o.addProperty("changed", changed);
        o.addProperty("hollow", hollow);
        o.addProperty("material", mat.getKey().toString());
        return o;
    }

    public static JsonObject batch(FileConfiguration config, JsonArray ops) {
        if (ops == null || ops.isEmpty()) {
            throw new ApiException(400, "ops array required");
        }
        if (ops.size() > 64) {
            throw new ApiException(400, "max 64 ops per batch");
        }

        JsonArray results = new JsonArray();
        int totalChanged = 0;
        for (JsonElement el : ops) {
            if (!el.isJsonObject()) {
                throw new ApiException(400, "each op must be object");
            }
            JsonObject op = el.getAsJsonObject();
            String type = op.has("op") ? op.get("op").getAsString().toLowerCase(Locale.ROOT) : "";
            JsonObject result = switch (type) {
                case "fill" -> {
                    Region region = RegionLimits.parseRegion(config, op);
                    String replace = op.has("replace") ? op.get("replace").getAsString() : null;
                    yield WorldAct.fill(region, op.get("material").getAsString(), replace);
                }
                case "setblock" -> WorldAct.setBlock(
                        op.get("world").getAsString(),
                        op.get("x").getAsInt(),
                        op.get("y").getAsInt(),
                        op.get("z").getAsInt(),
                        op.get("material").getAsString());
                case "box" -> {
                    Region region = RegionLimits.parseRegion(config, op);
                    String mode = op.has("mode") ? op.get("mode").getAsString() : "hollow";
                    yield box(region, op.get("material").getAsString(), mode);
                }
                case "line" -> line(
                        op.get("world").getAsString(),
                        op.get("x1").getAsInt(),
                        op.get("y1").getAsInt(),
                        op.get("z1").getAsInt(),
                        op.get("x2").getAsInt(),
                        op.get("y2").getAsInt(),
                        op.get("z2").getAsInt(),
                        op.get("material").getAsString());
                case "cylinder" -> cylinder(
                        op.get("world").getAsString(),
                        op.get("x").getAsInt(),
                        op.get("y").getAsInt(),
                        op.get("z").getAsInt(),
                        op.get("radius").getAsInt(),
                        op.has("height") ? op.get("height").getAsInt() : 1,
                        op.get("material").getAsString(),
                        op.has("hollow") && op.get("hollow").getAsBoolean());
                case "air" -> {
                    Region region = RegionLimits.parseRegion(config, op);
                    yield WorldAct.fill(region, "air", null);
                }
                default -> throw new ApiException(400,
                        "unknown op '" + type + "' (fill|setblock|box|line|cylinder|air)");
            };
            if (result.has("changed")) {
                totalChanged += result.get("changed").getAsInt();
            }
            result.addProperty("op", type);
            results.add(result);
        }

        JsonObject out = Json.obj();
        out.addProperty("ok", true);
        out.addProperty("ops", results.size());
        out.addProperty("total_changed", totalChanged);
        out.add("results", results);
        return out;
    }

    public static JsonObject wallsOnly(Region region, String materialName, boolean withFloor, boolean withRoof) {
        List<String> parts = new ArrayList<>();
        parts.add("walls");
        if (withFloor) {
            parts.add("floor");
        }
        if (withRoof) {
            parts.add("roof");
        }
        // emulate via box hollow then carve interior if needed — simpler: custom
        Material mat = requireBlock(materialName);
        int changed = 0;
        for (int x = region.minX; x <= region.maxX; x++) {
            for (int y = region.minY; y <= region.maxY; y++) {
                for (int z = region.minZ; z <= region.maxZ; z++) {
                    boolean onX = x == region.minX || x == region.maxX;
                    boolean onZ = z == region.minZ || z == region.maxZ;
                    boolean floor = withFloor && y == region.minY;
                    boolean roof = withRoof && y == region.maxY;
                    if (onX || onZ || floor || roof) {
                        Blocks.set(region.world.getBlockAt(x, y, z), mat);
                        changed++;
                    }
                }
            }
        }
        JsonObject o = Json.obj();
        o.addProperty("ok", true);
        o.addProperty("changed", changed);
        o.addProperty("material", mat.getKey().toString());
        return o;
    }

    private static World requireWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new ApiException(404, "World not found: " + worldName);
        }
        return world;
    }

    private static Material requireBlock(String materialName) {
        Material mat = Material.matchMaterial(materialName);
        if (mat == null || !mat.isBlock()) {
            throw new ApiException(400, "Invalid block material: " + materialName);
        }
        return mat;
    }
}
