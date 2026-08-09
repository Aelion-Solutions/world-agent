package sh.variiuz.worldagent.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandException;
import org.bukkit.configuration.file.FileConfiguration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import sh.variiuz.worldagent.api.ApiException;
import sh.variiuz.worldagent.api.Json;
import sh.variiuz.worldagent.tx.Blocks;
import sh.variiuz.worldagent.util.Region;
import sh.variiuz.worldagent.util.RegionLimits;
import sh.variiuz.worldagent.util.Worlds;

public final class WorldAct {

    private WorldAct() {
    }

    public static JsonObject setBlock(String worldName, int x, int y, int z, String materialName) {
        World world = Worlds.requireWorld(worldName);
        Material mat = Worlds.requireBlockMaterial(materialName);
        Blocks.set(world.getBlockAt(x, y, z), mat);
        JsonObject result = Json.obj();
        result.addProperty("ok", true);
        result.addProperty("world", worldName);
        result.addProperty("x", x);
        result.addProperty("y", y);
        result.addProperty("z", z);
        result.addProperty("material", mat.getKey().toString());
        return result;
    }

    public static JsonObject fill(Region region, String materialName, String replaceOnly) {
        Material mat = Worlds.requireBlockMaterial(materialName);
        Material replace = null;
        if (replaceOnly != null && !replaceOnly.isBlank()) {
            replace = Material.matchMaterial(replaceOnly);
            if (replace == null) {
                throw new ApiException(400, "Invalid replace material: " + replaceOnly);
            }
        }

        int changed = 0;
        for (int x = region.minX; x <= region.maxX; x++) {
            for (int y = region.minY; y <= region.maxY; y++) {
                for (int z = region.minZ; z <= region.maxZ; z++) {
                    Block block = region.world.getBlockAt(x, y, z);
                    if (replace != null && block.getType() != replace) {
                        continue;
                    }
                    Blocks.set(block, mat);
                    changed++;
                }
            }
        }

        JsonObject result = Json.obj();
        result.addProperty("ok", true);
        result.addProperty("changed", changed);
        result.addProperty("material", mat.getKey().toString());
        result.addProperty("volume", region.volume());
        return result;
    }

    /**
     * WA1 schematic format:
     * WA1
     * world|dx|dy|dz
     * relativeX,relativeY,relativeZ,material
     */
    public static JsonObject saveSchematic(Region region, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("WA1\n");
        int dx = region.edgeX();
        int dy = region.edgeY();
        int dz = region.edgeZ();
        sb.append(region.world.getName()).append('|').append(dx).append('|').append(dy).append('|').append(dz)
                .append('\n');

        int blocks = 0;
        for (int x = region.minX; x <= region.maxX; x++) {
            for (int y = region.minY; y <= region.maxY; y++) {
                for (int z = region.minZ; z <= region.maxZ; z++) {
                    Material mat = region.world.getBlockAt(x, y, z).getType();
                    if (mat.isAir()) {
                        continue;
                    }
                    sb.append(x - region.minX).append(',')
                            .append(y - region.minY).append(',')
                            .append(z - region.minZ).append(',')
                            .append(mat.getKey()).append('\n');
                    blocks++;
                }
            }
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);

        JsonObject result = Json.obj();
        result.addProperty("ok", true);
        result.addProperty("file", file.getFileName().toString());
        result.addProperty("blocks", blocks);
        result.addProperty("dx", dx);
        result.addProperty("dy", dy);
        result.addProperty("dz", dz);
        return result;
    }

    public static JsonObject pasteSchematic(FileConfiguration config, Path file, String worldName,
            int originX, int originY, int originZ) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new ApiException(404, "Schematic not found: " + file.getFileName());
        }
        World world = Worlds.requireWorld(worldName);
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Wa1Schematic schematic = Wa1Schematic.parse(lines);

        RegionLimits.validateBounds(config, world,
                originX, originY, originZ,
                originX + schematic.dx() - 1,
                originY + schematic.dy() - 1,
                originZ + schematic.dz() - 1);
        RegionLimits.ensureRequestBudget(config, schematic.entries().size());

        int placed = 0;
        for (Wa1Schematic.Entry entry : schematic.entries()) {
            Material mat = Worlds.requireBlockMaterial(entry.materialName());
            Blocks.set(world.getBlockAt(originX + entry.x(), originY + entry.y(), originZ + entry.z()), mat);
            placed++;
        }

        JsonObject result = Json.obj();
        result.addProperty("ok", true);
        result.addProperty("placed", placed);
        result.addProperty("origin_x", originX);
        result.addProperty("origin_y", originY);
        result.addProperty("origin_z", originZ);
        return result;
    }

    public static boolean isCommandAllowlisted(List<String> allowlist, String commandLine) {
        String cmd = commandLine == null ? "" : commandLine.trim();
        if (cmd.isEmpty()) {
            return false;
        }
        String lower = cmd.toLowerCase(Locale.ROOT);
        for (String prefix : allowlist) {
            if (lower.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static JsonObject runAllowlisted(List<String> allowlist, String commandLine) {
        String cmd = commandLine == null ? "" : commandLine.trim();
        if (cmd.isEmpty()) {
            throw new ApiException(400, "Missing command");
        }
        if (!isCommandAllowlisted(allowlist, cmd)) {
            throw new ApiException(403, "Command not allowlisted");
        }
        try {
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            JsonObject result = Json.obj();
            result.addProperty("ok", success);
            result.addProperty("command", cmd);
            return result;
        } catch (CommandException e) {
            throw new ApiException(500, "Command failed: " + e.getMessage());
        }
    }

    public static JsonArray listSchematics(Path dir) throws IOException {
        JsonArray arr = new JsonArray();
        if (!Files.isDirectory(dir)) {
            return arr;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".wa1"))
                    .forEach(p -> arr.add(p.getFileName().toString()));
        }
        return arr;
    }

    /**
     * Parsed WA1 schematic. Material names are kept as strings so parsing stays
     * free of Bukkit registry access (resolved at paste time).
     */
    public static final class Wa1Schematic {
        public record Entry(int x, int y, int z, String materialName) {
        }

        private final int dx;
        private final int dy;
        private final int dz;
        private final List<Entry> entries;

        private Wa1Schematic(int dx, int dy, int dz, List<Entry> entries) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.entries = entries;
        }

        public int dx() {
            return dx;
        }

        public int dy() {
            return dy;
        }

        public int dz() {
            return dz;
        }

        public List<Entry> entries() {
            return entries;
        }

        public static Wa1Schematic parse(List<String> lines) {
            if (lines == null || lines.isEmpty() || !lines.get(0).trim().equals("WA1")) {
                throw new ApiException(400, "Unsupported schematic format (need WA1)");
            }
            int dx = 1;
            int dy = 1;
            int dz = 1;
            if (lines.size() > 1) {
                String[] header = lines.get(1).trim().split("\\|");
                if (header.length >= 4) {
                    try {
                        dx = Math.max(1, Integer.parseInt(header[1]));
                        dy = Math.max(1, Integer.parseInt(header[2]));
                        dz = Math.max(1, Integer.parseInt(header[3]));
                    } catch (NumberFormatException e) {
                        throw new ApiException(400, "Invalid WA1 header dimensions");
                    }
                }
            }
            List<Entry> entries = new ArrayList<>();
            for (int i = 2; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length < 4) {
                    throw new ApiException(400, "Malformed WA1 block line: " + line);
                }
                try {
                    int rx = Integer.parseInt(parts[0]);
                    int ry = Integer.parseInt(parts[1]);
                    int rz = Integer.parseInt(parts[2]);
                    String materialName = parts[3].trim();
                    if (materialName.isEmpty()) {
                        throw new ApiException(400, "Malformed WA1 block line: " + line);
                    }
                    entries.add(new Entry(rx, ry, rz, materialName));
                } catch (NumberFormatException e) {
                    throw new ApiException(400, "Malformed WA1 block line: " + line);
                }
            }
            return new Wa1Schematic(dx, dy, dz, List.copyOf(entries));
        }
    }
}
