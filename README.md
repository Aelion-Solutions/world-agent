# Aelion World Agent

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Repo:** https://github.com/Aelion-Solutions/world-agent

Localhost **eyes + hands** for AI agents in Minecraft: a Paper plugin HTTP API and a thin Cursor MCP bridge.

Sense → plan → build → verify — without pasting F3 coordinates.

## Features

- **Sense** — worlds, players, region scans, slices, heightmaps, entities, POIs
- **Act** — setblock, fill, box, line, cylinder, batch, clipboard (WA1), allowlisted commands
- **Transactions** — undo / redo stack for mutations
- **Verify** — snapshots, diffs, material / emptiness asserts
- **MCP** — Cursor tools wrapping the same HTTP API

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

- Paper **1.21.x** (built against 1.21.11 API)
- Java **21**
- Node.js **20+** (MCP only)

## Quick start

### 1. Build the plugin

```bash
cd plugin
mvn -DskipTests package
```

Copy `plugin/target/AelionWorldAgent-0.1.0.jar` into your Paper `plugins/` folder.

### 2. Configure

On first boot the plugin writes `plugins/AelionWorldAgent/config.yml`. Set a strong token:

```yaml
http:
  host: 127.0.0.1
  port: 8765
  token: "replace-me-with-a-long-random-secret"
```

Restart Paper. Console should show:

```text
World Agent HTTP listening on http://127.0.0.1:8765/v1/
```

### 3. Smoke test

```bash
curl -H "Authorization: Bearer replace-me-with-a-long-random-secret" \
  http://127.0.0.1:8765/v1/health
```

### 4. Cursor MCP

```bash
cd mcp
cp .env.example .env   # edit WORLD_AGENT_TOKEN to match the plugin
npm install
npm run build
```

Add a server entry to your Cursor MCP config (see [docs/mcp.example.json](docs/mcp.example.json)):

- `command`: `node`
- `args`: absolute path to `mcp/dist/index.js`
- `env.WORLD_AGENT_URL` / `env.WORLD_AGENT_TOKEN`

Agent workflow notes: [docs/SKILL.md](docs/SKILL.md)

OpenAPI sketch: [schemas/openapi.yaml](schemas/openapi.yaml)

## API overview

| Area | Endpoints |
|------|-----------|
| Sense | `GET /v1/health`, `/worlds`, `/players`, `/scan`, `/slice`, `/heightmap`, `/block`, `/entities`, `/pois` |
| Markers | `POST /v1/markers` |
| Act | `POST /v1/setblock`, `/fill`, `/box`, `/line`, `/cylinder`, `/batch`, `/clipboard/*`, `/run` |
| Tx | `GET /v1/tx/list`, `POST /v1/tx/undo`, `/redo`, `/clear` |
| Verify | `POST /v1/assert/empty`, `/assert/materials`, `/snapshot`, `/diff` |

Region query: `world` + `x1,y1,z1,x2,y2,z2` **or** `x,y,z,radius`.

Schematics use a simple **WA1** text format under `plugins/AelionWorldAgent/schematics/`.

## Optional adapters

Disabled by default. When enabled in `config.yml`, POI adapters can surface data from companion plugins (no hard jar dependency):

- Config-file spawn / link reader (APM-shaped YAML)
- Live NPC registry via reflection (AelionNPCs, if present)

## In-game

```text
/worldagent status|reload|token|undo|redo|tx
```

Permission: `worldagent.admin` (default: op).

## Deploy helper

```powershell
.\scripts\deploy.ps1 -PluginsDir "C:\path\to\paper\plugins"
```

## License

[MIT](LICENSE) © Aelion Solutions
