import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { WorldAgentClient } from "../client.js";
import { regionShape, textResult } from "../schemas.js";

export function registerVerificationTools(server: McpServer, client: WorldAgentClient) {
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
}
