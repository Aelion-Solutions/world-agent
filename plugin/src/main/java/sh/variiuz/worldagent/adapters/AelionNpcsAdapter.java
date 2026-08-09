package sh.variiuz.worldagent.adapters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import sh.variiuz.worldagent.WorldAgentPlugin;
import sh.variiuz.worldagent.poi.Poi;

/**
 * Soft-reflects AelionNPCs when the plugin is present
 * ({@code getInstance().getNPCRegistry().getAll()}).
 */
public final class AelionNpcsAdapter implements PoiAdapter {

    private final WorldAgentPlugin plugin;

    public AelionNpcsAdapter(WorldAgentPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "aelion-npcs";
    }

    @Override
    public List<Poi> collect() {
        List<Poi> pois = new ArrayList<>();
        Plugin npcPlugin = Bukkit.getPluginManager().getPlugin("AelionNPCs");
        if (npcPlugin == null || !npcPlugin.isEnabled()) {
            return pois;
        }

        try {
            Object instance = npcPlugin.getClass().getMethod("getInstance").invoke(null);
            Object registry = instance.getClass().getMethod("getNPCRegistry").invoke(instance);
            Object all = registry.getClass().getMethod("getAll").invoke(registry);
            if (!(all instanceof Collection<?> npcs)) {
                plugin.getLogger().warning("AelionNPCs registry.getAll() did not return a Collection");
                return pois;
            }

            for (Object npc : npcs) {
                UUID uuid = (UUID) npc.getClass().getMethod("getUuid").invoke(npc);
                String name = (String) npc.getClass().getMethod("getName").invoke(npc);
                Location loc = (Location) npc.getClass().getMethod("getLocation").invoke(npc);
                if (loc == null || loc.getWorld() == null) {
                    continue;
                }
                pois.add(new Poi(
                        "aelion-npc:" + uuid,
                        "aelion-npcs",
                        name == null ? uuid.toString() : name,
                        loc.getWorld().getName(),
                        loc.getX(),
                        loc.getY(),
                        loc.getZ(),
                        Map.of("uuid", uuid.toString(), "kind", "npc")));
            }
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("AelionNPCs reflection failed: " + e.getMessage());
        }
        return pois;
    }
}
