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
import sh.variiuz.worldagent.util.HttpTokens;

public final class WorldAgentPlugin extends JavaPlugin {

    private ApiServer apiServer;
    private PoiStore poiStore;
    private SnapshotStore snapshotStore;
    private AdapterRegistry adapterRegistry;
    private TransactionManager transactions;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureHttpToken();
        poiStore = new PoiStore(this);
        transactions = new TransactionManager(this);
        Blocks.init(this);
        snapshotStore = new SnapshotStore(getConfig().getInt("snapshots.max_entries", 16));
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
        Blocks.clear();
        getLogger().info("Aelion World Agent disabled.");
    }

    public void reloadAll() {
        reloadConfig();
        ensureHttpToken();
        snapshotStore.setMaxEntries(getConfig().getInt("snapshots.max_entries", 16));
        adapterRegistry.reload();
        stopApi();
        startApi();
    }

    private void ensureHttpToken() {
        String token = getConfig().getString("http.token", "");
        if (!HttpTokens.isUnusable(token)) {
            return;
        }
        String generated = HttpTokens.generate();
        getConfig().set("http.token", generated);
        saveConfig();
        getLogger().warning("Generated a new http.token in config.yml (previous value was blank or a known placeholder).");
        getLogger().warning("Copy it into WORLD_AGENT_TOKEN for the MCP bridge. Use /worldagent token to see its length.");
    }

    private void startApi() {
        if (!getConfig().getBoolean("http.enabled", true)) {
            getLogger().warning("HTTP API disabled in config.");
            return;
        }
        if (HttpTokens.isUnusable(getConfig().getString("http.token", ""))) {
            getLogger().severe("HTTP API not started: http.token is blank or a known placeholder.");
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
