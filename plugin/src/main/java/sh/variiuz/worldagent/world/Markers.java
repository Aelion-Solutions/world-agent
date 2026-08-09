package sh.variiuz.worldagent.world;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.JsonObject;

import net.kyori.adventure.text.Component;
import sh.variiuz.worldagent.api.Json;
import sh.variiuz.worldagent.util.Worlds;

public final class Markers {

    private Markers() {
    }

    public static JsonObject place(JavaPlugin plugin, String worldName, double x, double y, double z,
            String label, int lifetimeTicks) {
        World world = Worlds.requireWorld(worldName);
        Location loc = new Location(world, x, y, z);
        ArmorStand stand = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setCustomNameVisible(true);
        stand.customName(Component.text(label == null ? "WA" : label));
        stand.addScoreboardTag("worldagent_marker");

        world.spawnParticle(Particle.DUST, loc, 30, 0.4, 0.8, 0.4, new Particle.DustOptions(Color.AQUA, 1.2f));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (stand.isValid()) {
                stand.remove();
            }
        }, Math.max(20L, lifetimeTicks));

        JsonObject o = Json.obj();
        o.addProperty("ok", true);
        o.addProperty("uuid", stand.getUniqueId().toString());
        o.addProperty("lifetime_ticks", lifetimeTicks);
        return o;
    }
}
