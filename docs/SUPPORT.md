# Support matrix

World Agent has two integration surfaces:

| Surface | What it is | Who can use it |
|---------|------------|----------------|
| **HTTP API** | Paper plugin on `127.0.0.1:8765` | Any local HTTP client (`curl`, scripts, custom agents) |
| **MCP (stdio)** | Node process wrapping that API | Any MCP host that can spawn a local stdio server |

This repo ships **stdio MCP only** (not remote Streamable HTTP / SSE). That matches localhost security: the agent must run on the same machine as Paper.

## Agents / MCP hosts

| Client | MCP stdio | Config style | Status | Notes |
|--------|-----------|--------------|--------|-------|
| **Cursor** | Yes | `mcpServers` in `.cursor/mcp.json` or user MCP settings | Supported | Primary development host |
| **Claude Desktop** | Yes | `mcpServers` in `claude_desktop_config.json` | Supported | Restart app after config changes |
| **Claude Code** | Yes | `mcpServers` in `.mcp.json` / user Claude settings | Supported | CLI + project config |
| **VS Code (GitHub Copilot)** | Yes | `servers` in `.vscode/mcp.json` | Supported | Root key is `servers`, not `mcpServers` |
| **Windsurf** | Yes | `mcpServers` in Windsurf MCP settings | Supported | Same shape as Cursor / Claude Desktop |
| **Cline** | Yes | `mcpServers` in Cline MCP settings | Supported | |
| **Continue** | Yes | MCP section in Continue config | Supported | |
| **Zed** | Yes | `context_servers` in settings | Community | Config key differs |
| **JetBrains AI** | Yes | IDE MCP / tools UI | Community | GUI-configured stdio |
| **Custom agent (MCP SDK)** | Yes | Spawn `node …/mcp/dist/index.js` | Supported | Any host implementing MCP stdio |
| **HTTP-only scripts** | N/A | Bearer token + JSON | Supported | No MCP required |
| **ChatGPT / Claude.ai (browser)** | No* | Remote connectors only | Not supported | Cannot spawn local stdio; do not expose World Agent publicly to “fix” this |
| **Cloud-hosted agent runners** | No* | Remote MCP / HTTP | Not supported* | Same machine requirement; keep API on loopback |

\* Browser / remote hosts need a **remote** MCP transport. World Agent intentionally stays loopback-only in v1 — do not tunnel it to the public internet.

## Same-machine requirement

```text
[ MCP host / agent ]  --stdio-->  [ world-agent mcp ]
                                        |
                                   HTTP Bearer
                                        v
                              [ Paper + World Agent ]
                                 127.0.0.1:8765
```

Paper, the MCP process, and the agent host should share one machine (or at least reach `127.0.0.1:8765` safely). Opening the port beyond loopback is unsupported.

## Minimal MCP env

Every stdio host needs:

| Variable | Example |
|----------|---------|
| `WORLD_AGENT_URL` | `http://127.0.0.1:8765` |
| `WORLD_AGENT_TOKEN` | same value as `plugins/AelionWorldAgent/config.yml` → `http.token` |

Command:

```bash
node /absolute/path/to/world-agent/mcp/dist/index.js
```

## Config snippets

### Cursor / Claude Desktop / Claude Code / Windsurf / Cline

Root key: `mcpServers` — see [mcp.example.json](mcp.example.json).

```json
{
  "mcpServers": {
    "aelion-world-agent": {
      "command": "node",
      "args": ["/absolute/path/to/world-agent/mcp/dist/index.js"],
      "env": {
        "WORLD_AGENT_URL": "http://127.0.0.1:8765",
        "WORLD_AGENT_TOKEN": "replace-me-with-a-long-random-secret"
      }
    }
  }
}
```

### VS Code (GitHub Copilot)

Root key: `servers` (and often an explicit `type`):

```json
{
  "servers": {
    "aelion-world-agent": {
      "type": "stdio",
      "command": "node",
      "args": ["/absolute/path/to/world-agent/mcp/dist/index.js"],
      "env": {
        "WORLD_AGENT_URL": "http://127.0.0.1:8765",
        "WORLD_AGENT_TOKEN": "replace-me-with-a-long-random-secret"
      }
    }
  }
}
```

### HTTP without MCP

```bash
curl -H "Authorization: Bearer replace-me-with-a-long-random-secret" \
  http://127.0.0.1:8765/v1/health
```

## Minecraft / runtime

| Component | Supported |
|-----------|-----------|
| Paper 1.21.x | Yes (built against 1.21.11 API) |
| Java 21+ | Yes (plugin) |
| Node.js 20+ | Yes (MCP) |
| Spigot / Bukkit / Fabric / Forge | Not targeted |
| Remote / shared public API | Not supported (v1) |

## Status legend

| Label | Meaning |
|-------|---------|
| **Supported** | Expected to work with the stock stdio MCP + documented config |
| **Community** | Should work via MCP stdio; config UI/keys vary — not CI-tested here |
| **Not supported** | Wrong transport or would require exposing the localhost API |

Tool/skill workflow for agents: [SKILL.md](SKILL.md).
