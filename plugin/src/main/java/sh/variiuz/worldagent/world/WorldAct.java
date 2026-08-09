package sh.variiuz.worldagent.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import sh.variiuz.worldagent.api.ApiException;
import sh.variiuz.worldagent.api.Json;
import sh.variiuz.worldagent.tx.Blocks;
import sh.variiuz.worldagent.util.Region;

public final class WorldAct {

    private WorldAct() {
    }

    public static JsonObject setBlock(String worldName, int x, int y, int z, String materialName) {
        var world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new ApiException(404, "World not found");
        }
        Material mat = Material.matchMaterial(materialName);
        if (mat == null || !mat.isBlock()) {
            throw new ApiException(400, "Invalid block material: " + materialName);
        }
        Blocks.set(world.getBlockAt(x, y, z), mat);
        JsonObject o = Json.obj();
        o.addProperty("ok", true);
        o.addProperty("world", worldName);
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        o.addProperty("material", mat.getKey().toString());
        return o;
    }

    public static JsonObject fill(Region region, String materialName, String replaceOnly) {
        Material mat = Material.matchMaterial(materialName);
        if (mat == null || !mat.isBlock()) {
            throw new ApiException(400, "Invalid block material: " + materialName);
        }
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

        JsonObject o = Json.obj();
        o.addProperty("ok", true);
        o.addProperty("changed", changed);
        o.addProperty("material", mat.getKey().toString());
        o.addProperty("volume", region.volume());
        return o;
    }

    /**
     * Simple schematic format (WA1): line-oriented
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
        sb.append(region.world.getName()).append('|').append(dx).append('|').append(dy).append('|').append(dz).append('\n');

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

        JsonObject o = Json.obj();
        o.addProperty("ok", true);
        o.addProperty("file", file.getFileName().toString());
        o.addProperty("blocks", blocks);
        o.addProperty("dx", dx);
        o.addProperty("dy", dy);
        o.addProperty("dz", dz);
        return o;
    }

    public static JsonObject pasteSchematic(Path file, String worldName, int originX, int originY, int originZ)
            throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new ApiException(404, "Schematic not found: " + file.getFileName());
        }
        var world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new ApiException(404, "World not found");
        }

        var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).trim().equals("WA1")) {
            throw new ApiException(400, "Unsupported schematic format (need WA1)");
        }

        int placed = 0;
        for (int i = 2; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length < 4) {
                continue;
            }
            int rx = Integer.parseInt(parts[0]);
            int ry = Integer.parseInt(parts[1]);
            int rz = Integer.parseInt(parts[2]);
            Material mat = Material.matchMaterial(parts[3]);
            if (mat == null || !mat.isBlock()) {
                continue;
            }
            Blocks.set(world.getBlockAt(originX + rx, originY + ry, originZ + rz), mat);
            placed++;
        }

        JsonObject o = Json.obj();
        o.addProperty("ok", true);
        o.addProperty("placed", placed);
        o.addProperty("origin_x", originX);
        o.addProperty("origin_y", originY);
        o.addProperty("origin_z", originZ);
        return o;
    }

    public static JsonObject runAllowlisted(java.util.List<String> allowlist, String commandLine) {
        String cmd = commandLine == null ? "" : commandLine.trim();
        if (cmd.isEmpty()) {
            throw new ApiException(400, "Missing command");
        }
        String lower = cmd.toLowerCase(Locale.ROOT);
        boolean ok = false;
        for (String prefix : allowlist) {
            if (lower.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            throw new ApiException(403, "Command not allowlisted");
        }
        try {
            boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            JsonObject o = Json.obj();
            o.addProperty("ok", success);
            o.addProperty("command", cmd);
            return o;
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
            stream.filter(p -> p.getFileName().toString().endsWith(".wa1")
                            || p.getFileName().toString().endsWith(".schem.txt"))
                    .forEach(p -> arr.add(p.getFileName().toString()));
        }
        return arr;
    }
}
