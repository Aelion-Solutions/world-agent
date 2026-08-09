package sh.variiuz.worldagent.tx;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import sh.variiuz.worldagent.WorldAgentPlugin;
import sh.variiuz.worldagent.api.ApiException;
import sh.variiuz.worldagent.api.Json;

/**
 * In-memory undo/redo for world mutations.
 * Records previous material the first time each block is touched in a transaction.
 * On undo, captures current materials so redo can restore the edit.
 */
public final class TransactionManager {

    private static final class Tx {
        final String id;
        final String label;
        final long startedAt;
        final long committedAt;
        /** world -> packed(x,y,z) -> material to restore when applying this tx */
        final Map<String, Map<Long, Material>> blocks;
        final int blockCount;

        Tx(String id, String label, long startedAt, long committedAt,
                Map<String, Map<Long, Material>> blocks, int blockCount) {
            this.id = id;
            this.label = label;
            this.startedAt = startedAt;
            this.committedAt = committedAt;
            this.blocks = blocks;
            this.blockCount = blockCount;
        }
    }

    private static final class OpenTx {
        final String id = UUID.randomUUID().toString();
        final String label;
        final long startedAt = System.currentTimeMillis();
        final Map<String, Map<Long, Material>> before = new HashMap<>();
        int recorded;

        OpenTx(String label) {
            this.label = (label == null || label.isBlank()) ? "edit" : label;
        }
    }

    private final WorldAgentPlugin plugin;
    private final Deque<Tx> undo = new ArrayDeque<>();
    private final Deque<Tx> redo = new ArrayDeque<>();
    private OpenTx current;

    public TransactionManager(WorldAgentPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean isOpen() {
        return current != null;
    }

    public synchronized String currentId() {
        return current == null ? null : current.id;
    }

    public synchronized void begin(String label) {
        if (current != null) {
            throw new ApiException(409, "Transaction already open: " + current.id);
        }
        if (!plugin.getConfig().getBoolean("transactions.enabled", true)) {
            current = new OpenTx(label); // still track but commit may no-op stack
        } else {
            current = new OpenTx(label);
        }
    }

    public synchronized void beginIfNeeded(String label) {
        if (current == null) {
            begin(label);
        }
    }

    public synchronized void record(Block block) {
        if (current == null || !plugin.getConfig().getBoolean("transactions.enabled", true)) {
            return;
        }
        int max = plugin.getConfig().getInt("transactions.max_blocks", 750_000);
        if (current.recorded >= max) {
            throw new ApiException(400, "Transaction exceeded max_blocks " + max);
        }
        String world = block.getWorld().getName();
        long key = pack(block.getX(), block.getY(), block.getZ());
        Map<Long, Material> map = current.before.computeIfAbsent(world, w -> new HashMap<>());
        if (!map.containsKey(key)) {
            map.put(key, block.getType());
            current.recorded++;
        }
    }

    public synchronized String commit() {
        if (current == null) {
            return null;
        }
        OpenTx open = current;
        current = null;
        if (!plugin.getConfig().getBoolean("transactions.enabled", true) || open.recorded == 0) {
            return open.id;
        }
        Tx tx = new Tx(open.id, open.label, open.startedAt, System.currentTimeMillis(),
                open.before, open.recorded);
        undo.addLast(tx);
        redo.clear();
        trim();
        return tx.id;
    }

    public synchronized void abort() {
        current = null;
    }

    public synchronized JsonObject undo() {
        if (undo.isEmpty()) {
            throw new ApiException(404, "Nothing to undo");
        }
        Tx tx = undo.removeLast();
        Tx redoImage = snapshotCurrent(tx, "redo:" + tx.label);
        applyMaterials(tx.blocks);
        redo.addLast(redoImage);
        trim();
        return statusJson(tx, "undone");
    }

    public synchronized JsonObject redo() {
        if (redo.isEmpty()) {
            throw new ApiException(404, "Nothing to redo");
        }
        Tx tx = redo.removeLast();
        Tx undoImage = snapshotCurrent(tx, tx.label);
        applyMaterials(tx.blocks);
        undo.addLast(undoImage);
        trim();
        return statusJson(tx, "redone");
    }

    public synchronized JsonObject undoTo(String id) {
        if (id == null || id.isBlank()) {
            return undo();
        }
        boolean found = undo.stream().anyMatch(t -> t.id.equals(id));
        if (!found) {
            throw new ApiException(404, "Unknown transaction: " + id);
        }
        int count = 0;
        JsonObject last = null;
        while (!undo.isEmpty()) {
            Tx top = undo.peekLast();
            last = undo();
            count++;
            if (top.id.equals(id)) {
                break;
            }
        }
        if (last != null) {
            last.addProperty("undone_count", count);
        }
        return last;
    }

    public synchronized JsonObject list() {
        JsonArray undoArr = new JsonArray();
        for (Tx tx : undo) {
            undoArr.add(summary(tx));
        }
        JsonArray redoArr = new JsonArray();
        for (Tx tx : redo) {
            redoArr.add(summary(tx));
        }
        JsonObject o = Json.obj();
        o.addProperty("enabled", plugin.getConfig().getBoolean("transactions.enabled", true));
        o.addProperty("open", current != null);
        if (current != null) {
            o.addProperty("open_id", current.id);
            o.addProperty("open_label", current.label);
            o.addProperty("open_blocks", current.recorded);
        }
        o.addProperty("undo_size", undo.size());
        o.addProperty("redo_size", redo.size());
        o.add("undo", undoArr);
        o.add("redo", redoArr);
        return o;
    }

    public synchronized JsonObject clear() {
        int u = undo.size();
        int r = redo.size();
        undo.clear();
        redo.clear();
        current = null;
        JsonObject o = Json.obj();
        o.addProperty("ok", true);
        o.addProperty("cleared_undo", u);
        o.addProperty("cleared_redo", r);
        return o;
    }

    private Tx snapshotCurrent(Tx source, String label) {
        Map<String, Map<Long, Material>> now = new HashMap<>();
        int count = 0;
        for (var worldEntry : source.blocks.entrySet()) {
            World world = Bukkit.getWorld(worldEntry.getKey());
            if (world == null) {
                continue;
            }
            Map<Long, Material> map = new HashMap<>();
            for (Long key : worldEntry.getValue().keySet()) {
                map.put(key, world.getBlockAt(unpackX(key), unpackY(key), unpackZ(key)).getType());
                count++;
            }
            now.put(worldEntry.getKey(), map);
        }
        return new Tx(UUID.randomUUID().toString(), label, System.currentTimeMillis(),
                System.currentTimeMillis(), now, count);
    }

    private void applyMaterials(Map<String, Map<Long, Material>> blocks) {
        for (var worldEntry : blocks.entrySet()) {
            World world = Bukkit.getWorld(worldEntry.getKey());
            if (world == null) {
                continue;
            }
            for (var e : worldEntry.getValue().entrySet()) {
                long key = e.getKey();
                world.getBlockAt(unpackX(key), unpackY(key), unpackZ(key)).setType(e.getValue(), false);
            }
        }
    }

    private void trim() {
        int max = plugin.getConfig().getInt("transactions.max_stack", 30);
        while (undo.size() > max) {
            undo.removeFirst();
        }
        while (redo.size() > max) {
            redo.removeFirst();
        }
    }

    private static JsonObject summary(Tx tx) {
        JsonObject o = Json.obj();
        o.addProperty("id", tx.id);
        o.addProperty("label", tx.label);
        o.addProperty("blocks", tx.blockCount);
        o.addProperty("started_at", tx.startedAt);
        o.addProperty("committed_at", tx.committedAt);
        return o;
    }

    private static JsonObject statusJson(Tx tx, String action) {
        JsonObject o = summary(tx);
        o.addProperty("ok", true);
        o.addProperty("action", action);
        return o;
    }

    static long pack(int x, int y, int z) {
        return (((long) (x + 30_000_000) & 0x3FFFFFFL) << 38)
                | (((long) (y + 2048) & 0xFFF) << 26)
                | ((long) (z + 30_000_000) & 0x3FFFFFFL);
    }

    static int unpackX(long key) {
        return (int) ((key >>> 38) & 0x3FFFFFFL) - 30_000_000;
    }

    static int unpackY(long key) {
        return (int) ((key >>> 26) & 0xFFFL) - 2048;
    }

    static int unpackZ(long key) {
        return (int) (key & 0x3FFFFFFL) - 30_000_000;
    }
}
