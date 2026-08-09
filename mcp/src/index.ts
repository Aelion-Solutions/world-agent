#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { WorldAgentClient } from "./client.js";
import { registerBuildTools } from "./tools/build.js";
import { registerSenseTools } from "./tools/sense.js";
import { registerTransactionTools } from "./tools/transactions.js";
import { registerVerificationTools } from "./tools/verification.js";

const baseUrl = process.env.WORLD_AGENT_URL ?? "http://127.0.0.1:8765";
const token = process.env.WORLD_AGENT_TOKEN;
if (!token || !token.trim()) {
  console.error(
    "WORLD_AGENT_TOKEN is required. Set it to the same value as http.token in plugins/AelionWorldAgent/config.yml.",
  );
  process.exit(1);
}

const client = new WorldAgentClient({ baseUrl, token: token.trim() });

const server = new McpServer({
  name: "aelion-world-agent",
  version: "0.1.0",
});

registerSenseTools(server, client);
registerBuildTools(server, client);
registerVerificationTools(server, client);
registerTransactionTools(server, client);

async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
