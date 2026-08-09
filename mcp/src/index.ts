#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import { WorldAgentClient } from "./client.js";

const baseUrl = process.env.WORLD_AGENT_URL ?? "http://127.0.0.1:8765";
const token = process.env.WORLD_AGENT_TOKEN ?? "change-me-world-agent";
const client = new WorldAgentClient({ baseUrl, token });

function textResult(data: unknown) {
  return {
    content: [{ type: "text" as const, text: JSON.stringify(data, null, 2) }],
  };
}

const regionShape = {
  world: z.string(),
  x1: z.number().int().optional(),
  y1: z.number().int().optional(),
  z1: z.number().int().optional(),
  x2: z.number().int().optional(),
  y2: z.number().int().optional(),
  z2: z.number().int().optional(),
  x: z.number().optional(),
  y: z.number().optional(),
  z: z.number().optional(),
  radius: z.number().optional(),
  radius_y: z.number().optional(),
};

const server = new McpServer({
  name: "aelion-world-agent",
  version: "0.2.0",
});

server.tool("world_health", "Check World Agent HTTP API health", {}, async () =>
  textResult(await client.get("/v1/health")),
);

server.tool("world_list", "List loaded worlds and spawn points", {}, async () =>
  textResult(await client.get("/v1/worlds")),
);

server.tool(
  "world_players",
  "List online players with exact coordinates (prefer this over entities for locating builders).",
  {},
  async () => textResult(await client.get("/v1/players")),
);

server.tool(
  "world_scan",
  "Scan a region: material counts, solid/air, dominant blocks. Prefer compact scans; use detail=blocks only for small regions.",
  {
    ...regionShape,
    detail: z.enum(["summary", "blocks"]).optional(),
  },
  async (args) => {
    const q: Record<string, string | number | undefined> = { world: args.world };
    for (const k of ["x1", "y1", "z1", "x2", "y2", "z2", "x", "y", "z", "radius", "radius_y"] as const) {
      if (args[k] !== undefined) q[k] = args[k] as number;
    }
    if (args.detail === "blocks") q.detail = "blocks";
    return textResult(await client.get("/v1/scan", q));
  },
);

server.tool(
  "world_slice",
  "Top-down material grid for a region (highest non-air per column).",
  regionShape,
  async (args) => {
    const q: Record<string, string | number | undefined> = { world: args.world };
    for (const k of ["x1", "y1", "z1", "x2", "y2", "z2", "x", "y", "z", "radius", "radius_y"] as const) {
      if (args[k] !== undefined) q[k] = args[k] as number;
    }
    return textResult(await client.get("/v1/slice", q));
  },
);

server.tool(
  "world_heightmap",
  "Highest non-air Y for each column in an XZ rectangle.",
  {
    world: z.string(),
    x1: z.number().int(),
    z1: z.number().int(),
    x2: z.number().int(),
    z2: z.number().int(),
    y_from: z.number().int().optional(),
    y_to: z.number().int().optional(),
  },
  async (args) =>
    textResult(
      await client.get("/v1/heightmap", {
        world: args.world,
        x1: args.x1,
        z1: args.z1,
        x2: args.x2,
        z2: args.z2,
        y_from: args.y_from ?? 0,
        y_to: args.y_to ?? 319,
      }),
    ),
);

server.tool(
  "world_block",
  "Get a single block material at x,y,z.",
  {
    world: z.string(),
    x: z.number().int(),
    y: z.number().int(),
    z: z.number().int(),
  },
  async (args) => textResult(await client.get("/v1/block", args)),
);

server.tool(
  "world_entities",
  "List nearby entities around a point.",
  {
    world: z.string(),
    x: z.number(),
    y: z.number(),
    z: z.number(),
    radius: z.number().optional(),
  },
  async (args) =>
    textResult(
      await client.get("/v1/entities", {
        world: args.world,
        x: args.x,
        y: args.y,
        z: args.z,
        radius: args.radius ?? 32,
      }),
    ),
);

server.tool(
  "world_pois",
  "List points of interest (manual + APM/Aelion adapters).",
  {},
  async () => textResult(await client.get("/v1/pois")),
);

server.tool(
  "world_marker",
  "Place a temporary armor-stand marker for human QA.",
  {
    world: z.string(),
    x: z.number(),
    y: z.number(),
    z: z.number(),
    label: z.string().optional(),
    lifetime_ticks: z.number().int().optional(),
    confirm: z.boolean().default(true),
  },
  async (args) => textResult(await client.post("/v1/markers", args)),
);

server.tool(
  "world_setblock",
  "Set a single block. Requires confirm:true.",
  {
    world: z.string(),
    x: z.number().int(),
    y: z.number().int(),
    z: z.number().int(),
    material: z.string(),
    confirm: z.boolean().default(true),
  },
  async (args) => textResult(await client.post("/v1/setblock", args)),
);

server.tool(
  "world_fill",
  "Fill/replace a region. Respect max_volume/max_edge. Requires confirm:true.",
  {
    ...regionShape,
    material: z.string(),
    replace: z.string().optional(),
    confirm: z.boolean().default(true),
  },
  async (args) => textResult(await client.post("/v1/fill", args)),
);

server.tool(
  "world_box",
  "Build a box: mode=solid|hollow|walls|frame. Best for rooms/buildings.",
  {
    ...regionShape,
    material: z.string(),
    mode: z.enum(["solid", "hollow", "walls", "frame"]).optional(),
    confirm: z.boolean().default(true),
  },
  async (args) => textResult(await client.post("/v1/box", { ...args, mode: args.mode ?? "hollow" })),
);

server.tool(
  "world_line",
  "Draw a straight block line between two points.",
  {
    world: z.string(),
    x1: z.number().int(),
    y1: z.number().int(),
    z1: z.number().int(),
    x2: z.number().int(),
    y2: z.number().int(),
    z2: z.number().int(),
    material: z.string(),
    confirm: z.boolean().default(true),
  },
  async (args) => textResult(await client.post("/v1/line", args)),
);

server.tool(
  "world_cylinder",
  "Place a vertical cylinder (optional hollow).",
  {
    world: z.string(),
    x: z.number().int(),
    y: z.number().int(),
    z: z.number().int(),
    radius: z.number().int(),
    height: z.number().int().optional(),
    material: z.string(),
    hollow: z.boolean().optional(),
    confirm: z.boolean().default(true),
  },
  async (args) =>
    textResult(
      await client.post("/v1/cylinder", {
        ...args,
        height: args.height ?? 1,
        hollow: args.hollow ?? false,
      }),
    ),
);

server.tool(
  "world_batch",
  "Run up to 64 build ops in one main-thread call: fill|setblock|box|line|cylinder|air. Prefer for multi-step builds.",
  {
    confirm: z.boolean().default(true),
    ops: z.array(z.record(z.string(), z.any())),
  },
  async (args) => textResult(await client.post("/v1/batch", args)),
);

server.tool(
  "world_clipboard_save",
  "Save a region to a WA1 schematic in plugin data folder.",
  {
    ...regionShape,
    name: z.string(),
    confirm: z.boolean().default(true),
  },
  async (args) => textResult(await client.post("/v1/clipboard/save", args)),
);

server.tool(
  "world_clipboard_paste",
  "Paste a WA1 schematic at origin.",
  {
    world: z.string(),
    name: z.string(),
    x: z.number().int(),
    y: z.number().int(),
    z: z.number().int(),
    confirm: z.boolean().default(true),
  },
  async (args) => textResult(await client.post("/v1/clipboard/paste", args)),
);

server.tool(
  "world_clipboard_list",
  "List saved schematics.",
  {},
  async () => textResult(await client.get("/v1/clipboard/list")),
);

server.tool(
  "world_run",
  "Run an allowlisted console command (tp, time, weather, …).",
  {
    command: z.string(),
    confirm: z.boolean().default(true),
  },
  async (args) => textResult(await client.post("/v1/run", args)),
);

server.tool(
  "world_assert_empty",
  "Assert a region is air-only.",
  regionShape,
  async (args) => textResult(await client.post("/v1/assert/empty", args)),
);

server.tool(
  "world_assert_materials",
  "Assert material fraction thresholds in a region.",
  {
    ...regionShape,
    min_fractions: z.record(z.string(), z.number()).optional(),
    max_fractions: z.record(z.string(), z.number()).optional(),
  },
  async (args) => textResult(await client.post("/v1/assert/materials", args)),
);

server.tool(
  "world_snapshot",
  "Capture a region snapshot for later diff.",
  regionShape,
  async (args) => textResult(await client.post("/v1/snapshot", args)),
);

server.tool(
  "world_diff",
  "Diff current world against a prior snapshot_id.",
  {
    snapshot_id: z.string(),
  },
  async (args) => textResult(await client.post("/v1/diff", args)),
);

server.tool(
  "world_tx_list",
  "List undo/redo transaction stacks (labels, block counts, ids).",
  {},
  async () => textResult(await client.get("/v1/tx/list")),
);

server.tool(
  "world_tx_undo",
  "Rollback the last world edit transaction (or undo to a specific id, undoing newer ones too).",
  {
    confirm: z.boolean().default(true),
    id: z.string().optional(),
  },
  async (args) => textResult(await client.post("/v1/tx/undo", args)),
);

server.tool(
  "world_tx_redo",
  "Re-apply the last undone transaction.",
  {
    confirm: z.boolean().default(true),
  },
  async (args) => textResult(await client.post("/v1/tx/redo", args)),
);

server.tool(
  "world_tx_clear",
  "Clear undo/redo history (does not change the world).",
  {
    confirm: z.boolean().default(true),
  },
  async (args) => textResult(await client.post("/v1/tx/clear", args)),
);

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
