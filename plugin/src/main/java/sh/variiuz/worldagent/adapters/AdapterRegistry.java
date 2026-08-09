package sh.variiuz.worldagent.adapters;

import java.util.ArrayList;
import java.util.List;

import sh.variiuz.worldagent.WorldAgentPlugin;
import sh.variiuz.worldagent.poi.Poi;

public final class AdapterRegistry {

    private final WorldAgentPlugin plugin;
    private final List<PoiAdapter> adapters = new ArrayList<>();

    public AdapterRegistry(WorldAgentPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        adapters.clear();
        if (!plugin.getConfig().getBoolean("adapters.enabled", false)) {
            return;
        }
        if (plugin.getConfig().getBoolean("adapters.apm.enabled", false)) {
            adapters.add(new ApmConfigAdapter(plugin));
        }
        if (plugin.getConfig().getBoolean("adapters.aelion_npcs.enabled", false)) {
            adapters.add(new AelionNpcsAdapter(plugin));
        }
        plugin.getLogger().info("POI adapters loaded: " + adapters.stream().map(PoiAdapter::name).toList());
    }

    public List<Poi> collectPois() {
        List<Poi> out = new ArrayList<>();
        for (PoiAdapter adapter : adapters) {
            try {
                out.addAll(adapter.collect());
            } catch (Exception e) {
                plugin.getLogger().warning("Adapter " + adapter.name() + " failed: " + e.getMessage());
            }
        }
        return out;
    }
}
