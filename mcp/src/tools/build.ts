import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { WorldAgentClient } from "../client.js";
import { regionShape, textResult } from "../schemas.js";

export function registerBuildTools(server: McpServer, client: WorldAgentClient) {
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
    },
    async (args) => textResult(await client.post("/v1/markers", args)),
  );

  server.tool(
    "world_setblock",
    "Set a single block.",
    {
      world: z.string(),
      x: z.number().int(),
      y: z.number().int(),
      z: z.number().int(),
      material: z.string(),
    },
    async (args) => textResult(await client.post("/v1/setblock", args)),
  );

  server.tool(
    "world_fill",
    "Fill/replace a region. Respect max_volume, max_edge, and max_blocks_per_request.",
    {
      ...regionShape,
      material: z.string(),
      replace: z.string().optional(),
    },
    async (args) => textResult(await client.post("/v1/fill", args)),
  );

  server.tool(
    "world_box",
    "Build a box: mode=solid|hollow|walls|frame.",
    {
      ...regionShape,
      material: z.string(),
      mode: z.enum(["solid", "hollow", "walls", "frame"]).optional(),
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
    "Run multiple build ops in one main-thread call: fill|setblock|box|line|cylinder|air.",
    {
      ops: z.array(z.record(z.string(), z.any())),
    },
    async (args) => textResult(await client.post("/v1/batch", args)),
  );

  server.tool(
    "world_clipboard_save",
    "Save a region to a WA1 schematic in the plugin data folder.",
    {
      ...regionShape,
      name: z.string(),
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
    },
    async (args) => textResult(await client.post("/v1/run", args)),
  );
}
