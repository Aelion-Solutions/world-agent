package sh.variiuz.worldagent.snapshot;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class SnapshotStore {

    private final LinkedHashMap<String, RegionSnapshot> snapshots = new LinkedHashMap<>();
    private int maxEntries;

    public SnapshotStore(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
    }

    public synchronized void setMaxEntries(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
        evict();
    }

    public synchronized String put(RegionSnapshot snapshot) {
        String id = UUID.randomUUID().toString();
        snapshots.put(id, snapshot);
        evict();
        return id;
    }

    public synchronized RegionSnapshot get(String id) {
        return snapshots.get(id);
    }

    public synchronized void clear() {
        snapshots.clear();
    }

    private void evict() {
        Iterator<Map.Entry<String, RegionSnapshot>> it = snapshots.entrySet().iterator();
        while (snapshots.size() > maxEntries && it.hasNext()) {
            it.next();
            it.remove();
        }
    }
}
