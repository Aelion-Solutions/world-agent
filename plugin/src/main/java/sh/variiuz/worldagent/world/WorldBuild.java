package sh.variiuz.worldagent.world;

import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import sh.variiuz.worldagent.api.ApiException;
import sh.variiuz.worldagent.api.Json;
import sh.variiuz.worldagent.tx.Blocks;
import sh.variiuz.worldagent.util.Region;
import sh.variiuz.worldagent.util.RegionLimits;
import sh.variiuz.worldagent.util.Worlds;

/** Build primitives: box, line, cylinder, batch. */
public final class WorldBuild {

    private WorldBuild() {
    }

    /** mode: solid | hollow | walls | frame */
    public static JsonObject box(Region region, String materialName, String mode) {
        Material mat = Worlds.requireBlockMaterial(materialName);
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
                        case "walls" -> onX || onZ;
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

        JsonObject result = Json.obj();
        result.addProperty("ok", true);
        result.addProperty("mode", m);
        result.addProperty("changed", changed);
        result.addProperty("material", mat.getKey().toString());
        result.addProperty("volume", region.volume());
        return result;
    }

    public static JsonObject line(FileConfiguration config, String worldName,
            int x1, int y1, int z1, int x2, int y2, int z2, String materialName) {
        World world = Worlds.requireWorld(worldName);
        Material mat = Worlds.requireBlockMaterial(materialName);
        RegionLimits.validateBounds(config, world, x1, y1, z1, x2, y2, z2);

        int dx = x2 - x1;
        int dy = y2 - y1;
        int dz = z2 - z1;
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        if (steps > 512) {
            throw new ApiException(400, "line too long (max 512)");
        }
        RegionLimits.ensureRequestBudget(config, steps + 1L);

        if (steps == 0) {
            Blocks.set(world.getBlockAt(x1, y1, z1), mat);
            JsonObject result = Json.obj();
            result.addProperty("ok", true);
            result.addProperty("changed", 1);
            return result;
        }

        int changed = 0;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (dx * i) / steps;
            int y = y1 + (dy * i) / steps;
            int z = z1 + (dz * i) / steps;
            Blocks.set(world.getBlockAt(x, y, z), mat);
            changed++;
        }
        JsonObject result = Json.obj();
        result.addProperty("ok", true);
        result.addProperty("changed", changed);
        result.addProperty("material", mat.getKey().toString());
        return result;
    }

    public static JsonObject cylinder(FileConfiguration config, String worldName,
            int cx, int cy, int cz, int radius, int height, String materialName, boolean hollow) {
        World world = Worlds.requireWorld(worldName);
        Material mat = Worlds.requireBlockMaterial(materialName);
        if (radius < 0 || radius > 64 || height < 1 || height > 128) {
            throw new ApiException(400, "radius 0-64, height 1-128");
        }
        RegionLimits.validateBounds(config, world,
                cx - radius, cy, cz - radius,
                cx + radius, cy + height - 1, cz + radius);

        long estimate = (long) (2 * radius + 1) * (2 * radius + 1) * height;
        RegionLimits.ensureRequestBudget(config, estimate);

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
        JsonObject result = Json.obj();
        result.addProperty("ok", true);
        result.addProperty("changed", changed);
        result.addProperty("hollow", hollow);
        result.addProperty("material", mat.getKey().toString());
        return result;
    }

    public static JsonObject batch(FileConfiguration config, JsonArray ops) {
        if (ops == null || ops.isEmpty()) {
            throw new ApiException(400, "ops array required");
        }
        int maxOps = RegionLimits.maxBatchOps(config);
        if (ops.size() > maxOps) {
            throw new ApiException(400, "max " + maxOps + " ops per batch");
        }

        long estimated = 0;
        for (JsonElement el : ops) {
            if (!el.isJsonObject()) {
                throw new ApiException(400, "each op must be object");
            }
            estimated += estimateOpCost(config, el.getAsJsonObject());
        }
        RegionLimits.ensureRequestBudget(config, estimated);

        JsonArray results = new JsonArray();
        int totalChanged = 0;
        for (JsonElement el : ops) {
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
                case "line" -> line(config,
                        op.get("world").getAsString(),
                        op.get("x1").getAsInt(),
                        op.get("y1").getAsInt(),
                        op.get("z1").getAsInt(),
                        op.get("x2").getAsInt(),
                        op.get("y2").getAsInt(),
                        op.get("z2").getAsInt(),
                        op.get("material").getAsString());
                case "cylinder" -> cylinder(config,
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

    private static long estimateOpCost(FileConfiguration config, JsonObject op) {
        String type = op.has("op") ? op.get("op").getAsString().toLowerCase(Locale.ROOT) : "";
        return switch (type) {
            case "fill", "air", "box" -> {
                // parseRegion validates limits; volume is a conservative upper bound
                yield RegionLimits.parseRegion(config, op).volume();
            }
            case "setblock" -> 1L;
            case "line" -> {
                int dx = Math.abs(op.get("x2").getAsInt() - op.get("x1").getAsInt());
                int dy = Math.abs(op.get("y2").getAsInt() - op.get("y1").getAsInt());
                int dz = Math.abs(op.get("z2").getAsInt() - op.get("z1").getAsInt());
                yield Math.max(Math.max(dx, dy), dz) + 1L;
            }
            case "cylinder" -> {
                int radius = op.get("radius").getAsInt();
                int height = op.has("height") ? op.get("height").getAsInt() : 1;
                yield (long) (2 * radius + 1) * (2 * radius + 1) * height;
            }
            default -> 0L;
        };
    }
}
