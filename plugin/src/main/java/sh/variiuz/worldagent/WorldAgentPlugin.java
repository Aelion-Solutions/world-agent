package sh.variiuz.worldagent;

import java.util.logging.Level;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import sh.variiuz.worldagent.adapters.AdapterRegistry;
import sh.variiuz.worldagent.api.ApiServer;
import sh.variiuz.worldagent.command.WorldAgentCommand;
import sh.variiuz.worldagent.poi.PoiStore;
import sh.variiuz.worldagent.snapshot.SnapshotStore;
import sh.variiuz.worldagent.tx.Blocks;
import sh.variiuz.worldagent.tx.TransactionManager;

public final class WorldAgentPlugin extends JavaPlugin {

    private ApiServer apiServer;
    private PoiStore poiStore;
    private SnapshotStore snapshotStore;
    private AdapterRegistry adapterRegistry;
    private TransactionManager transactions;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        poiStore = new PoiStore(this);
        transactions = new TransactionManager(this);
        Blocks.init(this);
        snapshotStore = new SnapshotStore();
        adapterRegistry = new AdapterRegistry(this);
        adapterRegistry.reload();

        startApi();

        WorldAgentCommand cmd = new WorldAgentCommand(this);
        PluginCommand pluginCommand = getCommand("worldagent");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(cmd);
            pluginCommand.setTabCompleter(cmd);
        }

        getLogger().info("Aelion World Agent enabled.");
    }

    @Override
    public void onDisable() {
        stopApi();
        getLogger().info("Aelion World Agent disabled.");
    }

    public void reloadAll() {
        reloadConfig();
        adapterRegistry.reload();
        stopApi();
        startApi();
    }

    private void startApi() {
        if (!getConfig().getBoolean("http.enabled", true)) {
            getLogger().warning("HTTP API disabled in config.");
            return;
        }
        try {
            apiServer = new ApiServer(this);
            apiServer.start();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to start HTTP API", e);
        }
    }

    private void stopApi() {
        if (apiServer != null) {
            apiServer.stop();
            apiServer = null;
        }
    }

    public ApiServer getApiServer() {
        return apiServer;
    }

    public PoiStore getPoiStore() {
        return poiStore;
    }

    public SnapshotStore getSnapshotStore() {
        return snapshotStore;
    }

    public AdapterRegistry getAdapterRegistry() {
        return adapterRegistry;
    }

    public TransactionManager getTransactions() {
        return transactions;
    }
}
