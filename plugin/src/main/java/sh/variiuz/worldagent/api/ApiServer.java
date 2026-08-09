package sh.variiuz.worldagent.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import sh.variiuz.worldagent.WorldAgentPlugin;
import sh.variiuz.worldagent.poi.Poi;
import sh.variiuz.worldagent.snapshot.RegionSnapshot;
import sh.variiuz.worldagent.util.Region;
import sh.variiuz.worldagent.util.RegionLimits;
import sh.variiuz.worldagent.world.Markers;
import sh.variiuz.worldagent.world.WorldAct;
import sh.variiuz.worldagent.world.WorldBuild;
import sh.variiuz.worldagent.world.WorldSense;
import sh.variiuz.worldagent.world.WorldVerify;

/**
 * JDK HttpServer bound to loopback only.
 */
public final class ApiServer {

    private final WorldAgentPlugin plugin;
    private HttpServer server;
    private volatile boolean running;

    public ApiServer(WorldAgentPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() throws IOException {
        String host = plugin.getConfig().getString("http.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("http.port", 8765);
        if (!"127.0.0.1".equals(host) && !"localhost".equalsIgnoreCase(host)) {
            plugin.getLogger().warning("Refusing non-loopback bind '" + host + "', forcing 127.0.0.1");
            host = "127.0.0.1";
        }

        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/v1/", this::dispatch);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        running = true;
        plugin.getLogger().info("World Agent HTTP listening on http://" + host + ":" + port + "/v1/");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        try {
            if (!authorize(exchange)) {
                writeJson(exchange, 401, error(401, "Unauthorized"));
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase();

            // CORS for local tools
            Headers h = exchange.getResponseHeaders();
            h.set("Access-Control-Allow-Origin", "http://127.0.0.1");
            h.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            switch (path) {
                case "/v1/health" -> syncGet(exchange, () -> WorldSense.health());
                case "/v1/worlds" -> syncGet(exchange, () -> {
                    JsonObject o = Json.obj();
                    o.add("worlds", WorldSense.worlds());
                    return o;
                });
                case "/v1/scan" -> handleScan(exchange);
                case "/v1/slice" -> handleSlice(exchange);
                case "/v1/entities" -> handleEntities(exchange);
                case "/v1/pois" -> handlePois(exchange, method);
                case "/v1/markers" -> handleMarkers(exchange, method);
                case "/v1/setblock" -> handleSetblock(exchange, method);
                case "/v1/fill" -> handleFill(exchange, method);
                case "/v1/clipboard/save" -> handleClipboardSave(exchange, method);
                case "/v1/clipboard/paste" -> handleClipboardPaste(exchange, method);
                case "/v1/clipboard/list" -> syncGet(exchange, () -> {
                    JsonObject o = Json.obj();
                    o.add("schematics", WorldAct.listSchematics(schematicsDir()));
                    return o;
                });
                case "/v1/run" -> handleRun(exchange, method);
                case "/v1/assert/empty" -> handleAssertEmpty(exchange, method);
                case "/v1/assert/materials" -> handleAssertMaterials(exchange, method);
                case "/v1/diff" -> handleDiff(exchange, method);
                case "/v1/snapshot" -> handleSnapshot(exchange, method);
                case "/v1/players" -> syncGet(exchange, WorldBuild::players);
                case "/v1/block" -> handleGetBlock(exchange);
                case "/v1/heightmap" -> handleHeightmap(exchange);
                case "/v1/box" -> handleBox(exchange, method);
                case "/v1/line" -> handleLine(exchange, method);
                case "/v1/cylinder" -> handleCylinder(exchange, method);
                case "/v1/batch" -> handleBatch(exchange, method);
                case "/v1/tx/list" -> syncGet(exchange, () -> plugin.getTransactions().list());
                case "/v1/tx/undo" -> handleTxUndo(exchange, method);
                case "/v1/tx/redo" -> handleTxRedo(exchange, method);
                case "/v1/tx/clear" -> handleTxClear(exchange, method);
                default -> writeJson(exchange, 404, error(404, "Not found: " + path));
            }
        } catch (ApiException e) {
            writeJson(exchange, e.getStatus(), error(e.getStatus(), e.getMessage()));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "API error", e);
            writeJson(exchange, 500, error(500, e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private boolean authorize(HttpExchange exchange) {
        String expected = plugin.getConfig().getString("http.token", "");
        if (expected == null || expected.isBlank()) {
            return false;
        }
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null) {
            return false;
        }
        String prefix = "Bearer ";
        if (!auth.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return false;
        }
        return expected.equals(auth.substring(prefix.length()).trim());
    }

    private void handleScan(HttpExchange exchange) throws Exception {
        Map<String, String> q = query(exchange);
        boolean detail = "blocks".equalsIgnoreCase(q.getOrDefault("detail", ""));
        syncGet(exchange, () -> {
            Region region = RegionLimits.parseQueryRegion(plugin.getConfig(), q);
            return WorldSense.scan(region, detail, 500);
        });
    }

    private void handleSlice(HttpExchange exchange) throws Exception {
        Map<String, String> q = query(exchange);
        syncGet(exchange, () -> WorldSense.slice(RegionLimits.parseQueryRegion(plugin.getConfig(), q)));
    }

    private void handleEntities(HttpExchange exchange) throws Exception {
        Map<String, String> q = query(exchange);
        syncGet(exchange, () -> {
            String worldName = q.get("world");
            if (worldName == null) {
                throw new ApiException(400, "Missing world");
            }
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                throw new ApiException(404, "World not found");
            }
            double x = Double.parseDouble(q.getOrDefault("x", "0"));
            double y = Double.parseDouble(q.getOrDefault("y", "64"));
            double z = Double.parseDouble(q.getOrDefault("z", "0"));
            double radius = Double.parseDouble(q.getOrDefault("radius", "32"));
            int max = plugin.getConfig().getInt("limits.max_entities", 200);
            return WorldSense.entities(world, x, y, z, radius, max);
        });
    }

    private void handlePois(HttpExchange exchange, String method) throws Exception {
        if ("GET".equals(method)) {
            syncGet(exchange, () -> {
                JsonObject o = Json.obj();
                o.add("pois", plugin.getPoiStore().allJson());
                return o;
            });
            return;
        }
        if ("POST".equals(method)) {
            JsonObject body = readBody(exchange);
            RegionLimits.requireConfirm(plugin.getConfig(), body);
            syncMutate(exchange, () -> {
                String id = body.has("id") ? body.get("id").getAsString() : java.util.UUID.randomUUID().toString();
                String name = body.has("name") ? body.get("name").getAsString() : id;
                String world = body.get("world").getAsString();
                double x = body.get("x").getAsDouble();
                double y = body.get("y").getAsDouble();
                double z = body.get("z").getAsDouble();
                plugin.getPoiStore().putManual(new Poi(id, "manual", name, world, x, y, z, Map.of("kind", "manual")));
                JsonObject o = Json.obj();
                o.addProperty("ok", true);
                o.addProperty("id", id);
                return o;
            });
            return;
        }
        writeJson(exchange, 405, error(405, "Method not allowed"));
    }

    private void handleMarkers(HttpExchange exchange, String method) throws Exception {
        if (!"POST".equals(method)) {
            writeJson(exchange, 405, error(405, "Method not allowed"));
            return;
        }
        JsonObject body = readBody(exchange);
        RegionLimits.requireConfirm(plugin.getConfig(), body);
        syncMutate(exchange, () -> Markers.place(
                plugin,
                body.get("world").getAsString(),
                body.get("x").getAsDouble(),
                body.get("y").getAsDouble(),
                body.get("z").getAsDouble(),
                body.has("label") ? body.get("label").getAsString() : "WA",
                body.has("lifetime_ticks")
                        ? body.get("lifetime_ticks").getAsInt()
                        : plugin.getConfig().getInt("markers.default_lifetime_ticks", 200)));
    }

    private void handleSetblock(HttpExchange exchange, String method) throws Exception {
        if (!"POST".equals(method)) {
            writeJson(exchange, 405, error(405, "Method not allowed"));
            return;
        }
        JsonObject body = readBody(exchange);
        RegionLimits.requireConfirm(plugin.getConfig(), body);
        syncMutate(exchange, () -> WorldAct.setBlock(
                body.get("world").getAsString(),
                body.get("x").getAsInt(),
                body.get("y").getAsInt(),
                body.get("z").getAsInt(),
                body.get("material").getAsString()));
    }

    private void handleFill(HttpExchange exchange, String method) throws Exception {
        if (!"POST".equals(method)) {
            writeJson(exchange, 405, error(405, "Method not allowed"));
            return;
        }
        JsonObject body = readBody(exchange);
        RegionLimits.requireConfirm(plugin.getConfig(), body);
        syncMutate(exchange, () -> {
            Region region = RegionLimits.parseRegion(plugin.getConfig(), body);
            String material = body.get("material").getAsString();
            String replace = body.has("replace") ? body.get("replace").getAsString() : null;
            return WorldAct.fill(region, material, replace);
        });
    }

    private void handleClipboardSave(HttpExchange exchange, String method) throws Exception {
        if (!"POST".equals(method)) {
            writeJson(exchange, 405, error(405, "Method not allowed"));
            return;
        }
        JsonObject body = readBody(exchange);
        RegionLimits.requireConfirm(plugin.getConfig(), body);
        syncMutate(exchange, () -> {
            Region region = RegionLimits.parseRegion(plugin.getConfig(), body);
            String name = body.has("name") ? body.get("name").getAsString() : "clipboard";
            name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path file = schematicsDir().resolve(name + ".wa1");
            try {
                return WorldAct.saveSchematic(region, file);
            } catch (IOException e) {
                throw new ApiException(500, e.getMessage());
            }
        });
    }

    private void handleClipboardPaste(HttpExchange exchange, String method) throws Exception {
        if (!"POST".equals(method)) {
            writeJson(exchange, 405, error(405, "Method not allowed"));
            return;
        }
        JsonObject body = readBody(exchange);
        RegionLimits.requireConfirm(plugin.getConfig(), body);
        syncMutate(exchange, () -> {
            String name = body.get("name").getAsString().replaceAll("[^a-zA-Z0-9._-]", "_");
            Path file = schematicsDir().resolve(name.endsWith(".wa1") ? name : name + ".wa1");
            try {
                return WorldAct.pasteSchematic(
                        file,
                        body.get("world").getAsString(),
                        body.get("x").getAsInt(),
                        body.get("y").getAsInt(),
                        body.get("z").getAsInt());
            } catch (IOException e) {
                throw new ApiException(500, e.getMessage());
            }
        });
    }

    private void handleRun(HttpExchange exchange, String method) throws Exception {
        if (!"POST".equals(method)) {
            writeJson(exchange, 405, error(405, "Method not allowed"));
            return;
        }
        JsonObject body = readBody(exchange);
        RegionLimits.requireConfirm(plugin.getConfig(), body);
        List<String> allow = plugin.getConfig().getStringList("commands.allowlist");
        syncMutate(exchange, () -> WorldAct.runAllowlisted(allow, body.get("command").getAsString()));
    }

    private void handleAssertEmpty(HttpExchange exchange, String method) throws Exception {
        JsonObject body = "POST".equals(method) ? readBody(exchange) : queryAsJson(exchange);
        syncGet(exchange, () -> WorldVerify.assertEmpty(RegionLimits.parseRegion(plugin.getConfig(), body)));
    }

    private void handleAssertMaterials(HttpExchange exchange, String method) throws Exception {
        if (!"POST".equals(method)) {
            writeJson(exchange, 405, error(405, "Method not allowed"));
            return;
        }
        JsonObject body = readBody(exchange);
        syncGet(exchange, () -> {
            Region region = RegionLimits.parseRegion(plugin.getConfig(), body);
            Map<String, Double> min = new HashMap<>();
            Map<String, Double> max = new HashMap<>();
            if (body.has("min_fractions") && body.get("min_fractions").isJsonObject()) {
                body.getAsJsonObject("min_fractions").entrySet()
                        .forEach(e -> min.put(e.getKey(), e.getValue().getAsDouble()));
            }
            if (body.has("max_fractions") && body.get("max_fractions").isJsonObject()) {
                body.getAsJsonObject("max_fractions").entrySet()
                        .forEach(e -> max.put(e.getKey(), e.getValue().getAsDouble()));
            }
            return WorldVerify.assertMaterials(region, min, max);
        });
    }

    private void handleSnapshot(HttpExchange exchange, String method) throws Exception {
        if (!"POST".equals(method)) {
            writeJson(exchange, 405, error(405, "Method not allowed"));
            return;
        }
        JsonObject body = readBody(exchange);
        syncGet(exchange, () -> {
            Region region = RegionLimits.parseRegion(plugin.getConfig(), body);
            RegionSnapshot snap = WorldVerify.capture(region);
            String id = plugin.getSnapshotStore().put(snap);
            JsonObject o = Json.obj();
            o.addProperty("ok", true);
            o.addProperty("snapshot_id", id);
            o.addProperty("volume", region.volume());
            return o;
        });
    }

    private void handleDiff(HttpExchange exchange, String method) throws Exception {
        if (!"POST".equals(method)) {
            writeJson(exchange, 405, error(405, "Method not allowed"));
            return;
        }
        JsonObject body = readBody(exchange);
        syncGet(exchange, () -> {
            String id = body.get("snapshot_id").getAsString();
            RegionSnapshot snap = plugin.getSnapshotStore().get(id);
            if (snap == null) {
                throw new ApiException(404, "Unknown snapshot_id");
            }
            Region region = new Region(
                    Bukkit.getWorld(snap.world()),
                    snap.minX(), snap.minY(), snap.minZ(),
                    snap.maxX(), snap.maxY(), snap.maxZ());
            if (region.world == null) {
                throw new ApiException(404, "World missing for snapshot");
            }
            return WorldVerify.diff(snap, region);
        });
    }


    private void handleGetBlock(HttpExchange exchange) throws Exception {
        Map<String, String> q = query(exchange);
        syncGet(exchange, () -> WorldBuild.getBlock(
                q.get("world"),
                Integer.parseInt(q.getOrDefault("x", "0")),
                Integer.parseInt(q.getOrDefault("y", "64")),
                Integer.parseInt(q.getOrDefault("z", "0"))));
    }

    private void handleHeightmap(HttpExchange exchange) throws Exception {
        Map<String, String> q = query(exchange);
        syncGet(exchange, () -> WorldBuild.heightmap(
                q.get("world"),
                Integer.parseInt(q.get("x1")),
                Integer.parseInt(q.get("z1")),
                Integer.parseInt(q.get("x2")),
                Integer.parseInt(q.get("z2")),
                Integer.parseInt(q.getOrDefault("y_from", "0")),
                Integer.parseInt(q.getOrDefault("y_to", "319"))));
    }

    private void handleBox(HttpExchange exchange, String method) throws Exception {
        if (!"POST".equals(method)) {
            writeJson(exchange, 405, error(405, "Method not allowed"));
            return;
        }
        JsonObject body = readBody(exchange);
        RegionLimits.requireConfirm(plugin.getConfig(), body);
        syncMutate(exchange, () -> {
            Region region = RegionLimits.parseRegion(plugin.getConfig(), body);
            String mode = body.has("mode") ? body.get("mode").getAsString() : "hollow";
            return WorldBuild.box(region, body.get("material").getAsString(), mode);
        });
    }

    private void handleLine(HttpExchange exchange, String method) throws Exception {
        if (!"POST".equals(method)) {
            writeJson(exchange, 405, error(405, "Method not allowed"));
            return;
        }
        JsonObject body = readBody(exchange);
        RegionLimits.requireConfirm(plugin.getConfig(), body);
        syncMutate(exchange, () -> WorldBuild.line(
                body.get("world").getAsString(),
                body.get("x1").getAsInt(),
                body.get("y1").getAsInt(),
                body.get("z1").getAsInt(),
                body.get("x2").getAsInt(),
                body.get("y2").getAsInt(),
                body.get("z2").getAsInt(),
                body.get("material").getAsString()));
    }

    private void handleCylinder(HttpExchange exchange, String method) throws Exception {
        if (!"POST".equals(method)) {
            writeJson(exchange, 405, error(405, "Method not allowed"));
            return;
        }
        JsonObject body = readBody(exchange);
        RegionLimits.requireConfirm(plugin.getConfig(), body);
        syncMutate(exchange, () -> WorldBuild.cylinder(
                body.get("world").getAsString(),
                body.get("x").getAsInt(),
                body.get("y").getAsInt(),
                body.get("z").getAsInt(),
                body.get("radius").getAsInt(),
                body.has("height") ? body.get("height").getAsInt() : 1,
                body.get("material").getAsString(),
                body.has("hollow") && body.get("hollow").getAsBoolean()));
    }

    private void handleBatch(HttpExchange exchange, String method) throws Exception {
        if (!"POST".equals(method)) {
            writeJson(exchange, 405, error(405, "Method not allowed"));
            return;
        }
        JsonObject body = readBody(exchange);
        RegionLimits.requireConfirm(plugin.getConfig(), body);
        JsonArray ops = body.has("ops") && body.get("ops").isJsonArray()
                ? body.getAsJsonArray("ops")
                : null;
        syncMutate(exchange, () -> WorldBuild.batch(plugin.getConfig(), ops));
    }
    private Path schematicsDir() {
        Path dir = plugin.getDataFolder().toPath().resolve("schematics");
        dir.toFile().mkdirs();
        return dir;
    }

    private interface SupplierEx {
        JsonObject get() throws Exception;
    }

    private void syncGet(HttpExchange exchange, SupplierEx supplier) throws Exception {
        Object[] box = new Object[1];
        Exception[] err = new Exception[1];
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                box[0] = supplier.get();
            } catch (Exception e) {
                err[0] = e;
            }
            synchronized (box) {
                box.notifyAll();
            }
        });
        synchronized (box) {
            long deadline = System.currentTimeMillis() + 60_000;
            while (box[0] == null && err[0] == null && System.currentTimeMillis() < deadline) {
                box.wait(200);
            }
        }
        if (err[0] instanceof ApiException api) {
            throw api;
        }
        if (err[0] != null) {
            throw err[0];
        }
        if (box[0] == null) {
            throw new ApiException(504, "Main-thread timeout");
        }
        writeJson(exchange, 200, (JsonObject) box[0]);
    }

    private void syncMutate(HttpExchange exchange, SupplierEx supplier) throws Exception {
        syncGet(exchange, () -> {
            String label = exchange.getRequestURI().getPath().replace("/v1/", "api:");
            plugin.getTransactions().beginIfNeeded(label);
            try {
                JsonObject result = supplier.get();
                String txId = plugin.getTransactions().commit();
                if (result != null && txId != null) {
                    result.addProperty("tx_id", txId);
                }
                return result;
            } catch (Exception e) {
                plugin.getTransactions().abort();
                throw e;
            }
        });
    }

    private void handleTxUndo(HttpExchange exchange, String method) throws Exception {
        if (!"POST".equals(method)) {
            writeJson(exchange, 405, error(405, "Method not allowed"));
            return;
        }
        JsonObject body = readBody(exchange);
        RegionLimits.requireConfirm(plugin.getConfig(), body);
        syncGet(exchange, () -> {
            if (body.has("id") && !body.get("id").getAsString().isBlank()) {
                return plugin.getTransactions().undoTo(body.get("id").getAsString());
            }
            return plugin.getTransactions().undo();
        });
    }

    private void handleTxRedo(HttpExchange exchange, String method) throws Exception {
        if (!"POST".equals(method)) {
            writeJson(exchange, 405, error(405, "Method not allowed"));
            return;
        }
        JsonObject body = readBody(exchange);
        RegionLimits.requireConfirm(plugin.getConfig(), body);
        syncGet(exchange, () -> plugin.getTransactions().redo());
    }

    private void handleTxClear(HttpExchange exchange, String method) throws Exception {
        if (!"POST".equals(method)) {
            writeJson(exchange, 405, error(405, "Method not allowed"));
            return;
        }
        JsonObject body = readBody(exchange);
        RegionLimits.requireConfirm(plugin.getConfig(), body);
        syncGet(exchange, () -> plugin.getTransactions().clear());
    }

    private static JsonObject readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Json.parseObject(body);
        }
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> map = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                map.put(decode(part), "");
            } else {
                map.put(decode(part.substring(0, eq)), decode(part.substring(eq + 1)));
            }
        }
        return map;
    }

    private static JsonObject queryAsJson(HttpExchange exchange) {
        JsonObject o = Json.obj();
        query(exchange).forEach((k, v) -> {
            if (v.matches("-?\\d+")) {
                o.addProperty(k, Integer.parseInt(v));
            } else if (v.matches("-?\\d+\\.\\d+")) {
                o.addProperty(k, Double.parseDouble(v));
            } else {
                o.addProperty(k, v);
            }
        });
        return o;
    }

    private static String decode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static JsonObject error(int status, String message) {
        JsonObject o = Json.obj();
        o.addProperty("ok", false);
        o.addProperty("status", status);
        o.addProperty("error", message);
        return o;
    }

    private static void writeJson(HttpExchange exchange, int status, JsonObject body) throws IOException {
        byte[] bytes = Json.GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
