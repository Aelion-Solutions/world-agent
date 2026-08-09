# Aelion World Agent (AWA)
Local world tooling for AI-assisted Minecraft development.

**eyes + hands** for AI agents in Minecraft


---

Paper plugin HTTP API (loopback) plus a thin MCP stdio bridge so local agents can sense and edit a live Minecraft world.

Built for fun — see what agents do when they can measure coordinates and place blocks without someone pasting F3 screenshots.

## Features

- **Sense** — worlds, players, region scans, slices, heightmaps, entities, POIs
- **Act** — setblock, fill, box, line, cylinder, batch, clipboard (WA1), allowlisted commands
- **Transactions** — undo / redo stack for mutations
- **Verify** — snapshots, diffs, material / emptiness asserts
- **MCP** — stdio tools wrapping the same HTTP API (any compatible host)

## Agent / MCP support

| Host | Works? |
|------|--------|
| Cursor, Claude Desktop, Claude Code, VS Code Copilot, Windsurf, Cline, Continue | Yes (stdio MCP) |
| Custom agents using an MCP SDK | Yes |
| `curl` / scripts (HTTP only) | Yes |
| Browser / cloud agents (ChatGPT, Claude.ai connectors) | No — local stdio / loopback only |

Full matrix, config keys, and snippets: **[docs/SUPPORT.md](docs/SUPPORT.md)**.

## Security model

Designed for **local agent use only**:

| Guard | Behavior |
|-------|----------|
| Bind address | Forced to `127.0.0.1` (non-loopback hosts are rejected) |
| Auth | `Authorization: Bearer <token>` on every request |
| Mutations | Require JSON `"confirm": true` (configurable) |
| Commands | Allowlisted prefixes only (`POST /v1/run`) |

Do **not** expose this port on a public interface.

## Requirements

- Paper 1.21.x (built against 1.21.11 API)
- Java 21
- Node.js 20+ (MCP only)

## Installation

```bash
cd plugin
mvn test package
```

Copy `plugin/target/AelionWorldAgent-0.1.0.jar` into the Paper `plugins/` folder and start the server.

On first enable the plugin writes `plugins/AelionWorldAgent/config.yml` and generates `http.token` if it is blank or a known placeholder. Copy that token into the MCP env (see below). Console should show:

```text
World Agent HTTP listening on http://127.0.0.1:8765/v1/
```

## Configuration

Important keys in `config.yml`:

| Key | Purpose |
|-----|---------|
| `http.host` / `http.port` | Loopback bind (non-loopback hosts are forced to `127.0.0.1`) |
| `http.token` | Bearer secret (generated if unusable) |
| `limits.max_volume` / `max_edge` | Region size caps |
| `limits.max_blocks_per_request` | Per-request mutation budget (default 250000) |
| `limits.max_batch_ops` | Max ops in `/v1/batch` |
| `mutations.enabled` | Kill-switch for writes |
| `commands.allowlist` | Prefixes for `/v1/run` |
| `transactions.*` | Undo stack size and per-tx block cap |
| `snapshots.max_entries` | In-memory snapshot LRU size |
| `adapters.*` | Optional POI sources (disabled by default) |

## MCP usage

```bash
cd mcp
npm install
npm test
npm run build
```

Point your MCP host at `mcp/dist/index.js` with:

- `WORLD_AGENT_URL=http://127.0.0.1:8765`
- `WORLD_AGENT_TOKEN=<same as http.token>`

`WORLD_AGENT_TOKEN` is required; the bridge does not fall back to a shared default.

Examples: [docs/mcp.example.json](docs/mcp.example.json), [docs/mcp.vscode.example.json](docs/mcp.vscode.example.json).  
Agent loop notes: [docs/SKILL.md](docs/SKILL.md). Host matrix: [docs/SUPPORT.md](docs/SUPPORT.md).

## API overview

| Area | Endpoints |
|------|-----------|
| Sense | `GET /v1/health`, `/worlds`, `/players`, `/scan`, `/slice`, `/heightmap`, `/block`, `/entities`, `/pois` |
| Markers | `POST /v1/markers` |
| Act | `POST /v1/setblock`, `/fill`, `/box`, `/line`, `/cylinder`, `/batch`, `/clipboard/*`, `/run` |
| Tx | `GET /v1/tx/list`, `POST /v1/tx/undo`, `/redo`, `/clear` |
| Verify | `POST /v1/assert/empty`, `/assert/materials`, `/snapshot`, `/diff` |

Region query: `world` + `x1,y1,z1,x2,y2,z2` or `x,y,z,radius`.  
Schematics use WA1 text files under `plugins/AelionWorldAgent/schematics/`.  
OpenAPI sketch: [schemas/openapi.yaml](schemas/openapi.yaml).

Successful mutations that change blocks return `tx_id` for undo. Failed mutations restore recorded before-materials and do not leave an undo entry.

## In-game

```text
/worldagent status|reload|token|undo|redo|tx
```

Permission: `worldagent.admin` (default: op).

## Development

```bash
# Plugin
cd plugin && mvn test package

# MCP
cd mcp && npm test && npm run build
```

Unit tests cover coordinate packing, transaction abort rollback bookkeeping, region math, WA1 parsing, command allowlist, tokens, snapshot eviction, and the MCP HTTP client.

## Security

- Binds to loopback only
- Every request needs `Authorization: Bearer <token>`
- Mutation size limited by region and per-request block budgets
- `/v1/run` is prefix-allowlisted
- Do not expose the port on a public interface

Details: [SECURITY.md](SECURITY.md).

## License

[MIT](LICENSE) © Aelion Solutions
