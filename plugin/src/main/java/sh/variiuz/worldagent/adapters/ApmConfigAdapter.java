package sh.variiuz.worldagent.adapters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import sh.variiuz.worldagent.WorldAgentPlugin;
import sh.variiuz.worldagent.poi.Poi;

/**
 * Optional POI adapter: reads spawn/link entries from a companion plugin config.yml
 * (no hard dependency on that plugin's jar).
 */
public final class ApmConfigAdapter implements PoiAdapter {

    private final WorldAgentPlugin plugin;

    public ApmConfigAdapter(WorldAgentPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "apm-config";
    }

    @Override
    public List<Poi> collect() {
        List<Poi> pois = new ArrayList<>();
        String rel = plugin.getConfig().getString("adapters.apm.config_path", "plugins/APM/config.yml");
        File file = new File(plugin.getServer().getWorldContainer(), rel);
        if (!file.isFile()) {
            return pois;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection spawns = yaml.getConfigurationSection("spawns");
        if (spawns != null) {
            for (String faction : spawns.getKeys(false)) {
                ConfigurationSection factionSec = spawns.getConfigurationSection(faction);
                if (factionSec == null) {
                    continue;
                }
                for (String spawnName : factionSec.getKeys(false)) {
                    ConfigurationSection s = factionSec.getConfigurationSection(spawnName);
                    if (s == null) {
                        continue;
                    }
                    String world = s.getString("world", "world");
                    double x = s.getDouble("x");
                    double y = s.getDouble("y");
                    double z = s.getDouble("z");
                    String npcId = s.getString("npc_id", "");
                    String id = "apm-spawn:" + faction + ":" + spawnName;
                    pois.add(new Poi(id, "apm", spawnName, world, x, y, z,
                            Map.of("faction", faction, "npc_id", npcId == null ? "" : npcId, "kind", "spawn")));
                }
            }
        }

        ConfigurationSection links = yaml.getConfigurationSection("npc_world_links");
        if (links != null) {
            for (String key : links.getKeys(false)) {
                String contactId = links.getString(key, "");
                String id = "apm-link:" + key;
                pois.add(new Poi(id, "apm", contactId, "world", 0, 0, 0,
                        Map.of("aelion_key", key, "contact_id", contactId == null ? "" : contactId, "kind", "npc_link")));
            }
        }

        return pois;
    }
}
