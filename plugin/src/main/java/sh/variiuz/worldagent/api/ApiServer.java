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
import sh.variiuz.worldagent.util.Worlds;
import sh.variiuz.worldagent.world.Markers;
import sh.variiuz.worldagent.world.WorldAct;
import sh.variiuz.worldagent.world.WorldBuild;
import sh.variiuz.worldagent.world.WorldSense;
import sh.variiuz.worldagent.world.WorldVerify;

/** JDK HttpServer bound to loopback only. */
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

            Headers headers = exchange.getResponseHeaders();
            headers.set("Access-Control-Allow-Origin", "http://127.0.0.1");
            headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            switch (path) {
                case "/v1/health" -> syncGet(exchange, WorldSense::health);
                case "/v1/worlds" -> syncGet(exchange, () -> {
                    JsonObject result = Json.obj();
                    result.add("worlds", WorldSense.worlds());
                    return result;
                });
                case "/v1/scan" -> handleScan(exchange);
                case "/v1/slice" -> handleSlice(exchange);
                case "/v1/entities" -> handleEntities(exchange);
                case "/v1/pois" -> handlePois(exchange, method);
                case "/v1/markers" -> postMutate(exchange, method, body -> Markers.place(
                        plugin,
                        body.get("world").getAsString(),
                        body.get("x").getAsDouble(),
                        body.get("y").getAsDouble(),
                        body.get("z").getAsDouble(),
                        body.has("label") ? body.get("label").getAsString() : "WA",
                        body.has("lifetime_ticks")
                                ? body.get("lifetime_ticks").getAsInt()
                                : plugin.getConfig().getInt("markers.default_lifetime_ticks", 200)));
                case "/v1/setblock" -> postMutate(exchange, method, body -> WorldAct.setBlock(
                        body.get("world").getAsString(),
                        body.get("x").getAsInt(),
                        body.get("y").getAsInt(),
                        body.get("z").getAsInt(),
                        body.get("material").getAsString()));
                case "/v1/fill" -> postMutate(exchange, method, body -> {
                    Region region = RegionLimits.parseRegion(plugin.getConfig(), body);
                    String material = body.get("material").getAsString();
                    String replace = body.has("replace") ? body.get("replace").getAsString() : null;
                    return WorldAct.fill(region, material, replace);
                });
                case "/v1/clipboard/save" -> postMutate(exchange, method, body -> {
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
                case "/v1/clipboard/paste" -> postMutate(exchange, method, body -> {
                    String name = body.get("name").getAsString().replaceAll("[^a-zA-Z0-9._-]", "_");
                    Path file = schematicsDir().resolve(name.endsWith(".wa1") ? name : name + ".wa1");
                    try {
                        return WorldAct.pasteSchematic(
                                plugin.getConfig(),
                                file,
                                body.get("world").getAsString(),
                                body.get("x").getAsInt(),
                                body.get("y").getAsInt(),
                                body.get("z").getAsInt());
                    } catch (IOException e) {
                        throw new ApiException(500, e.getMessage());
                    }
                });
                case "/v1/clipboard/list" -> syncGet(exchange, () -> {
                    JsonObject result = Json.obj();
                    result.add("schematics", WorldAct.listSchematics(schematicsDir()));
                    return result;
                });
                case "/v1/run" -> postMutate(exchange, method, body -> {
                    List<String> allow = plugin.getConfig().getStringList("commands.allowlist");
                    return WorldAct.runAllowlisted(allow, body.get("command").getAsString());
                });
                case "/v1/assert/empty" -> handleAssertEmpty(exchange, method);
                case "/v1/assert/materials" -> postRead(exchange, method, body -> {
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
                case "/v1/diff" -> postRead(exchange, method, body -> {
                    String id = body.get("snapshot_id").getAsString();
                    RegionSnapshot snap = plugin.getSnapshotStore().get(id);
                    if (snap == null) {
                        throw new ApiException(404, "Unknown snapshot_id");
                    }
                    World world = Worlds.requireWorld(snap.world());
                    Region region = new Region(world, snap.minX(), snap.minY(), snap.minZ(),
                            snap.maxX(), snap.maxY(), snap.maxZ());
                    return WorldVerify.diff(snap, region);
                });
                case "/v1/snapshot" -> postRead(exchange, method, body -> {
                    Region region = RegionLimits.parseRegion(plugin.getConfig(), body);
                    RegionSnapshot snap = WorldVerify.capture(region);
                    String id = plugin.getSnapshotStore().put(snap);
                    JsonObject result = Json.obj();
                    result.addProperty("ok", true);
                    result.addProperty("snapshot_id", id);
                    result.addProperty("volume", region.volume());
                    return result;
                });
                case "/v1/players" -> syncGet(exchange, WorldSense::players);
                case "/v1/block" -> handleGetBlock(exchange);
                case "/v1/heightmap" -> handleHeightmap(exchange);
                case "/v1/box" -> postMutate(exchange, method, body -> {
                    Region region = RegionLimits.parseRegion(plugin.getConfig(), body);
                    String mode = body.has("mode") ? body.get("mode").getAsString() : "hollow";
                    return WorldBuild.box(region, body.get("material").getAsString(), mode);
                });
                case "/v1/line" -> postMutate(exchange, method, body -> WorldBuild.line(
                        plugin.getConfig(),
                        body.get("world").getAsString(),
                        body.get("x1").getAsInt(),
                        body.get("y1").getAsInt(),
                        body.get("z1").getAsInt(),
                        body.get("x2").getAsInt(),
                        body.get("y2").getAsInt(),
                        body.get("z2").getAsInt(),
                        body.get("material").getAsString()));
                case "/v1/cylinder" -> postMutate(exchange, method, body -> WorldBuild.cylinder(
                        plugin.getConfig(),
                        body.get("world").getAsString(),
                        body.get("x").getAsInt(),
                        body.get("y").getAsInt(),
                        body.get("z").getAsInt(),
                        body.get("radius").getAsInt(),
                        body.has("height") ? body.get("height").getAsInt() : 1,
                        body.get("material").getAsString(),
                        body.has("hollow") && body.get("hollow").getAsBoolean()));
                case "/v1/batch" -> postMutate(exchange, method, body -> {
                    JsonArray ops = body.has("ops") && body.get("ops").isJsonArray()
                            ? body.getAsJsonArray("ops")
                            : null;
                    return WorldBuild.batch(plugin.getConfig(), ops);
                });
                case "/v1/tx/list" -> syncGet(exchange, () -> plugin.getTransactions().list());
                case "/v1/tx/undo" -> postRead(exchange, method, body -> {
                    if (body.has("id") && !body.get("id").getAsString().isBlank()) {
                        return plugin.getTransactions().undoTo(body.get("id").getAsString());
                    }
                    return plugin.getTransactions().undo();
                });
                case "/v1/tx/redo" -> postRead(exchange, method, body -> plugin.getTransactions().redo());
                case "/v1/tx/clear" -> postRead(exchange, method, body -> plugin.getTransactions().clear());
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
            World world = Worlds.requireWorld(q.get("world"));
            double x = Double.parseDouble(q.getOrDefault("x", "0"));
            double y = Double.parseDouble(q.getOrDefault("y", "64"));
            double z = Double.parseDouble(q.getOrDefault("z", "0"));
            double radius = Double.parseDouble(q.getOrDefault("radius", "32"));
            int max = plugin.getConfig().getInt("limits.max_entities", 250);
            return WorldSense.entities(world, x, y, z, radius, max);
        });
    }

    private void handlePois(HttpExchange exchange, String method) throws Exception {
        if ("GET".equals(method)) {
            syncGet(exchange, () -> {
                JsonObject result = Json.obj();
                result.add("pois", plugin.getPoiStore().allJson());
                return result;
            });
            return;
        }
        if ("POST".equals(method)) {
            postMutate(exchange, method, body -> {
                String id = body.has("id") ? body.get("id").getAsString() : java.util.UUID.randomUUID().toString();
                String name = body.has("name") ? body.get("name").getAsString() : id;
                String world = body.get("world").getAsString();
                double x = body.get("x").getAsDouble();
                double y = body.get("y").getAsDouble();
                double z = body.get("z").getAsDouble();
                plugin.getPoiStore().putManual(new Poi(id, "manual", name, world, x, y, z, Map.of("kind", "manual")));
                JsonObject result = Json.obj();
                result.addProperty("ok", true);
                result.addProperty("id", id);
                return result;
            });
            return;
        }
        writeJson(exchange, 405, error(405, "Method not allowed"));
    }

    private void handleAssertEmpty(HttpExchange exchange, String method) throws Exception {
        JsonObject body = "POST".equals(method) ? readBody(exchange) : queryAsJson(exchange);
        syncGet(exchange, () -> WorldVerify.assertEmpty(RegionLimits.parseRegion(plugin.getConfig(), body)));
    }

    private void handleGetBlock(HttpExchange exchange) throws Exception {
        Map<String, String> q = query(exchange);
        syncGet(exchange, () -> WorldSense.getBlock(
                q.get("world"),
                Integer.parseInt(q.getOrDefault("x", "0")),
                Integer.parseInt(q.getOrDefault("y", "64")),
                Integer.parseInt(q.getOrDefault("z", "0"))));
    }

    private void handleHeightmap(HttpExchange exchange) throws Exception {
        Map<String, String> q = query(exchange);
        syncGet(exchange, () -> WorldSense.heightmap(
                q.get("world"),
                Integer.parseInt(q.get("x1")),
                Integer.parseInt(q.get("z1")),
                Integer.parseInt(q.get("x2")),
                Integer.parseInt(q.get("z2")),
                Integer.parseInt(q.getOrDefault("y_from", "0")),
                Integer.parseInt(q.getOrDefault("y_to", "319"))));
    }

    private Path schematicsDir() {
        Path dir = plugin.getDataFolder().toPath().resolve("schematics");
        dir.toFile().mkdirs();
        return dir;
    }

    private interface SupplierEx {
        JsonObject get() throws Exception;
    }

    private interface BodyHandler {
        JsonObject handle(JsonObject body) throws Exception;
    }

    private static void requirePost(String method) {
        if (!"POST".equals(method)) {
            throw new ApiException(405, "Method not allowed");
        }
    }

    private void postMutate(HttpExchange exchange, String method, BodyHandler handler) throws Exception {
        requirePost(method);
        JsonObject body = readBody(exchange);
        RegionLimits.requireMutationsEnabled(plugin.getConfig());
        syncMutate(exchange, () -> handler.handle(body));
    }

    private void postRead(HttpExchange exchange, String method, BodyHandler handler) throws Exception {
        requirePost(method);
        JsonObject body = readBody(exchange);
        syncGet(exchange, () -> handler.handle(body));
    }

    private void syncGet(HttpExchange exchange, SupplierEx supplier) throws Exception {
        Object[] resultHolder = new Object[1];
        Exception[] err = new Exception[1];
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                resultHolder[0] = supplier.get();
            } catch (Exception e) {
                err[0] = e;
            }
            synchronized (resultHolder) {
                resultHolder.notifyAll();
            }
        });
        synchronized (resultHolder) {
            long deadline = System.currentTimeMillis() + 60_000;
            while (resultHolder[0] == null && err[0] == null && System.currentTimeMillis() < deadline) {
                resultHolder.wait(200);
            }
        }
        if (err[0] instanceof ApiException api) {
            throw api;
        }
        if (err[0] != null) {
            throw err[0];
        }
        if (resultHolder[0] == null) {
            plugin.getLogger().warning(
                    "Main-thread timeout after 60s; the scheduled task may still be running.");
            throw new ApiException(504, "Main-thread timeout");
        }
        writeJson(exchange, 200, (JsonObject) resultHolder[0]);
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

    private static JsonObject readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Json.parseObject(body);
        }
    }

    private static JsonObject queryAsJson(HttpExchange exchange) {
        JsonObject result = Json.obj();
        query(exchange).forEach((k, v) -> {
            if (v.matches("-?\\d+")) {
                result.addProperty(k, Integer.parseInt(v));
            } else if (v.matches("-?\\d+\\.\\d+")) {
                result.addProperty(k, Double.parseDouble(v));
            } else {
                result.addProperty(k, v);
            }
        });
        return result;
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) {
            return params;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                params.put(decode(part), "");
            } else {
                params.put(decode(part.substring(0, eq)), decode(part.substring(eq + 1)));
            }
        }
        return params;
    }

    private static String decode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static JsonObject error(int status, String message) {
        JsonObject result = Json.obj();
        result.addProperty("ok", false);
        result.addProperty("status", status);
        result.addProperty("error", message);
        return result;
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
