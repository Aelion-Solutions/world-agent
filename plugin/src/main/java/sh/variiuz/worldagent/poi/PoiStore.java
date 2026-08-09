package sh.variiuz.worldagent.poi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import sh.variiuz.worldagent.WorldAgentPlugin;
import sh.variiuz.worldagent.api.Json;

public final class PoiStore {

    private final WorldAgentPlugin plugin;
    private final Map<String, Poi> manual = new ConcurrentHashMap<>();

    public PoiStore(WorldAgentPlugin plugin) {
        this.plugin = plugin;
    }

    public void putManual(Poi poi) {
        manual.put(poi.id(), poi);
    }

    public JsonArray allJson() {
        List<Poi> all = new ArrayList<>(manual.values());
        all.addAll(plugin.getAdapterRegistry().collectPois());
        JsonArray arr = new JsonArray();
        for (Poi poi : all) {
            JsonObject o = Json.obj();
            o.addProperty("id", poi.id());
            o.addProperty("source", poi.source());
            o.addProperty("name", poi.name());
            o.addProperty("world", poi.world());
            o.addProperty("x", poi.x());
            o.addProperty("y", poi.y());
            o.addProperty("z", poi.z());
            JsonObject tags = Json.obj();
            if (poi.tags() != null) {
                poi.tags().forEach(tags::addProperty);
            }
            o.add("tags", tags);
            arr.add(o);
        }
        return arr;
    }
}
