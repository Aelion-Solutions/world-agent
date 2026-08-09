import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { WorldAgentClient } from "../client.js";
import { textResult } from "../schemas.js";

export function registerTransactionTools(server: McpServer, client: WorldAgentClient) {
  server.tool(
    "world_tx_list",
    "List undo/redo transaction stacks (labels, block counts, ids).",
    {},
    async () => textResult(await client.get("/v1/tx/list")),
  );

  server.tool(
    "world_tx_undo",
    "Undo the last committed world edit (or undo back to a specific id).",
    {
      id: z.string().optional(),
    },
    async (args) => textResult(await client.post("/v1/tx/undo", args)),
  );

  server.tool(
    "world_tx_redo",
    "Re-apply the last undone transaction.",
    {},
    async () => textResult(await client.post("/v1/tx/redo", {})),
  );

  server.tool(
    "world_tx_clear",
    "Clear undo/redo history. Open edits are rolled back; committed history is dropped.",
    {},
    async () => textResult(await client.post("/v1/tx/clear", {})),
  );
}
