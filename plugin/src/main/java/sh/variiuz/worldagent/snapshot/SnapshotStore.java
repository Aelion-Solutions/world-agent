package sh.variiuz.worldagent.snapshot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SnapshotStore {

    private final Map<String, RegionSnapshot> snapshots = new ConcurrentHashMap<>();

    public String put(RegionSnapshot snapshot) {
        String id = UUID.randomUUID().toString();
        snapshots.put(id, snapshot);
        return id;
    }

    public RegionSnapshot get(String id) {
        return snapshots.get(id);
    }

    public void clear() {
        snapshots.clear();
    }
}
