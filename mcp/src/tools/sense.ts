import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { WorldAgentClient } from "../client.js";
import { regionQuery, regionShape, textResult } from "../schemas.js";

export function registerSenseTools(server: McpServer, client: WorldAgentClient) {
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
      const q = regionQuery(args);
      if (args.detail === "blocks") q.detail = "blocks";
      return textResult(await client.get("/v1/scan", q));
    },
  );

  server.tool(
    "world_slice",
    "Top-down material grid for a region (highest non-air per column).",
    regionShape,
    async (args) => textResult(await client.get("/v1/slice", regionQuery(args))),
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
    "List points of interest (manual + optional adapters).",
    {},
    async () => textResult(await client.get("/v1/pois")),
  );
}
