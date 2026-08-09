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
 * Failed mutations call {@link #abort()}, which restores recorded before-materials.
 * Undo stores Material only (not BlockData / tile entities).
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

    public synchronized void begin(String label) {
        if (current != null) {
            throw new ApiException(409, "Transaction already open: " + current.id);
        }
        current = new OpenTx(label);
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
        int maxTx = plugin.getConfig().getInt("transactions.max_blocks", 250_000);
        int maxReq = plugin.getConfig().getInt("limits.max_blocks_per_request", 250_000);
        int max = Math.min(maxTx, maxReq);
        if (current.recorded >= max) {
            throw new ApiException(400, "Transaction exceeded block budget " + max);
        }
        String world = block.getWorld().getName();
        long key = pack(block.getX(), block.getY(), block.getZ());
        Map<Long, Material> byCoord = current.before.computeIfAbsent(world, w -> new HashMap<>());
        if (!byCoord.containsKey(key)) {
            byCoord.put(key, block.getType());
            current.recorded++;
        }
    }

    /**
     * Commits the open transaction onto the undo stack.
     * Returns null when nothing was recorded (or transactions are disabled), so callers
     * do not advertise a non-undoable tx_id.
     */
    public synchronized String commit() {
        if (current == null) {
            return null;
        }
        OpenTx open = current;
        current = null;
        if (!plugin.getConfig().getBoolean("transactions.enabled", true) || open.recorded == 0) {
            return null;
        }
        Tx tx = new Tx(open.id, open.label, open.startedAt, System.currentTimeMillis(),
                open.before, open.recorded);
        undo.addLast(tx);
        redo.clear();
        trim();
        return tx.id;
    }

    /**
     * Drops the open transaction and restores every recorded before-material in the world.
     * Committed undo/redo stacks are left unchanged.
     */
    public synchronized void abort() {
        OpenTx open = current;
        current = null;
        if (open == null || open.recorded == 0) {
            return;
        }
        applyMaterials(open.before);
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
        JsonObject result = Json.obj();
        result.addProperty("enabled", plugin.getConfig().getBoolean("transactions.enabled", true));
        result.addProperty("open", current != null);
        if (current != null) {
            result.addProperty("open_id", current.id);
            result.addProperty("open_label", current.label);
            result.addProperty("open_blocks", current.recorded);
        }
        result.addProperty("undo_size", undo.size());
        result.addProperty("redo_size", redo.size());
        result.add("undo", undoArr);
        result.add("redo", redoArr);
        return result;
    }

    public synchronized JsonObject clear() {
        abort();
        int u = undo.size();
        int r = redo.size();
        undo.clear();
        redo.clear();
        JsonObject result = Json.obj();
        result.addProperty("ok", true);
        result.addProperty("cleared_undo", u);
        result.addProperty("cleared_redo", r);
        return result;
    }

    /** Package-visible for tests: how many blocks are recorded in the open transaction. */
    synchronized int openRecorded() {
        return current == null ? 0 : current.recorded;
    }

    /** Package-visible for tests: whether a transaction is open. */
    synchronized boolean hasOpen() {
        return current != null;
    }

    /** Package-visible for tests: undo stack size. */
    synchronized int undoSize() {
        return undo.size();
    }

    private Tx snapshotCurrent(Tx source, String label) {
        Map<String, Map<Long, Material>> now = new HashMap<>();
        int count = 0;
        for (var worldEntry : source.blocks.entrySet()) {
            World world = Bukkit.getWorld(worldEntry.getKey());
            if (world == null) {
                continue;
            }
            Map<Long, Material> byCoord = new HashMap<>();
            for (Long key : worldEntry.getValue().keySet()) {
                byCoord.put(key, world.getBlockAt(unpackX(key), unpackY(key), unpackZ(key)).getType());
                count++;
            }
            now.put(worldEntry.getKey(), byCoord);
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
            for (var entry : worldEntry.getValue().entrySet()) {
                long key = entry.getKey();
                world.getBlockAt(unpackX(key), unpackY(key), unpackZ(key)).setType(entry.getValue(), false);
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
        JsonObject result = Json.obj();
        result.addProperty("id", tx.id);
        result.addProperty("label", tx.label);
        result.addProperty("blocks", tx.blockCount);
        result.addProperty("started_at", tx.startedAt);
        result.addProperty("committed_at", tx.committedAt);
        return result;
    }

    private static JsonObject statusJson(Tx tx, String action) {
        JsonObject result = summary(tx);
        result.addProperty("ok", true);
        result.addProperty("action", action);
        return result;
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
