package sh.variiuz.worldagent.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.block.Block;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import sh.variiuz.worldagent.api.Json;
import sh.variiuz.worldagent.snapshot.RegionSnapshot;
import sh.variiuz.worldagent.util.Region;

public final class WorldVerify {

    private WorldVerify() {
    }

    public static JsonObject assertEmpty(Region region) {
        int solid = 0;
        JsonArray samples = new JsonArray();
        for (int x = region.minX; x <= region.maxX; x++) {
            for (int y = region.minY; y <= region.maxY; y++) {
                for (int z = region.minZ; z <= region.maxZ; z++) {
                    Block b = region.world.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) {
                        solid++;
                        if (samples.size() < 20) {
                            JsonObject s = Json.obj();
                            s.addProperty("x", x);
                            s.addProperty("y", y);
                            s.addProperty("z", z);
                            s.addProperty("material", b.getType().getKey().toString());
                            samples.add(s);
                        }
                    }
                }
            }
        }
        JsonObject o = Json.obj();
        o.addProperty("ok", solid == 0);
        o.addProperty("solid", solid);
        o.addProperty("volume", region.volume());
        o.add("samples", samples);
        return o;
    }

    public static JsonObject assertMaterials(Region region, Map<String, Double> minFractions,
            Map<String, Double> maxFractions) {
        Map<String, Integer> counts = new HashMap<>();
        long volume = region.volume();
        for (int x = region.minX; x <= region.maxX; x++) {
            for (int y = region.minY; y <= region.maxY; y++) {
                for (int z = region.minZ; z <= region.maxZ; z++) {
                    String key = region.world.getBlockAt(x, y, z).getType().getKey().toString();
                    counts.merge(key, 1, Integer::sum);
                }
            }
        }

        JsonArray failures = new JsonArray();
        if (minFractions != null) {
            for (var e : minFractions.entrySet()) {
                double frac = counts.getOrDefault(e.getKey(), 0) / (double) volume;
                if (frac < e.getValue()) {
                    JsonObject f = Json.obj();
                    f.addProperty("material", e.getKey());
                    f.addProperty("actual", frac);
                    f.addProperty("min", e.getValue());
                    failures.add(f);
                }
            }
        }
        if (maxFractions != null) {
            for (var e : maxFractions.entrySet()) {
                double frac = counts.getOrDefault(e.getKey(), 0) / (double) volume;
                if (frac > e.getValue()) {
                    JsonObject f = Json.obj();
                    f.addProperty("material", e.getKey());
                    f.addProperty("actual", frac);
                    f.addProperty("max", e.getValue());
                    failures.add(f);
                }
            }
        }

        JsonObject o = Json.obj();
        o.addProperty("ok", failures.isEmpty());
        o.addProperty("volume", volume);
        JsonObject countsJson = Json.obj();
        counts.forEach(countsJson::addProperty);
        o.add("counts", countsJson);
        o.add("failures", failures);
        return o;
    }

    public static RegionSnapshot capture(Region region) {
        Map<Long, Material> map = new HashMap<>();
        for (int x = region.minX; x <= region.maxX; x++) {
            for (int y = region.minY; y <= region.maxY; y++) {
                for (int z = region.minZ; z <= region.maxZ; z++) {
                    long key = pack(x - region.minX, y - region.minY, z - region.minZ);
                    map.put(key, region.world.getBlockAt(x, y, z).getType());
                }
            }
        }
        return new RegionSnapshot(region.world.getName(), region.minX, region.minY, region.minZ,
                region.maxX, region.maxY, region.maxZ, map);
    }

    public static JsonObject diff(RegionSnapshot before, Region region) {
        if (!before.world().equals(region.world.getName())
                || before.minX() != region.minX || before.minY() != region.minY || before.minZ() != region.minZ
                || before.maxX() != region.maxX || before.maxY() != region.maxY || before.maxZ() != region.maxZ) {
            JsonObject err = Json.obj();
            err.addProperty("ok", false);
            err.addProperty("error", "Snapshot region mismatch");
            return err;
        }

        int changed = 0;
        JsonArray samples = new JsonArray();
        Set<Long> keys = new HashSet<>(before.blocks().keySet());

        for (int x = region.minX; x <= region.maxX; x++) {
            for (int y = region.minY; y <= region.maxY; y++) {
                for (int z = region.minZ; z <= region.maxZ; z++) {
                    long key = pack(x - region.minX, y - region.minY, z - region.minZ);
                    keys.add(key);
                    Material was = before.blocks().getOrDefault(key, Material.AIR);
                    Material now = region.world.getBlockAt(x, y, z).getType();
                    if (was != now) {
                        changed++;
                        if (samples.size() < 40) {
                            JsonObject s = Json.obj();
                            s.addProperty("x", x);
                            s.addProperty("y", y);
                            s.addProperty("z", z);
                            s.addProperty("before", was.getKey().toString());
                            s.addProperty("after", now.getKey().toString());
                            samples.add(s);
                        }
                    }
                }
            }
        }

        JsonObject o = Json.obj();
        o.addProperty("ok", true);
        o.addProperty("changed", changed);
        o.addProperty("volume", region.volume());
        o.add("samples", samples);
        return o;
    }

    private static long pack(int x, int y, int z) {
        return (((long) x & 0xFFFFF) << 40) | (((long) y & 0xFFFFF) << 20) | ((long) z & 0xFFFFF);
    }
}
