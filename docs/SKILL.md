---
name: aelion-world-agent
description: >-
  Sense, plan, build, and verify inside a live Paper world via the Aelion World
  Agent HTTP/MCP tools. Use when placing builds, checking coords, or validating
  regions without relying on the user pasting F3 data.
---

# Aelion World Agent

## Prerequisites

1. Paper server running with `AelionWorldAgent-*.jar` in `plugins/`
2. `plugins/AelionWorldAgent/config.yml` token set (not the default)
3. Cursor MCP pointed at `mcp/dist/index.js` with:
   - `WORLD_AGENT_URL=http://127.0.0.1:8765`
   - `WORLD_AGENT_TOKEN=<same token>`

## Loop

```text
sense → plan → act → verify → fix
```

1. **Sense** — `world_health`, `world_list`, `world_players`, `world_pois`, `world_scan`, `world_slice`, `world_entities`
2. **Plan** — pick AABB, materials, schematic names; stay under `max_edge` / `max_volume`
3. **Act** — `world_marker`, then `world_fill` / `world_box` / `world_batch` / clipboard paste (`confirm: true`)
4. **Verify** — `world_snapshot` before edits, then `world_diff`; or `world_assert_empty` / `world_assert_materials`
5. **Fix** — `world_tx_undo` / `world_tx_redo` when a batch went wrong

## Hard rules

- API is **127.0.0.1 only**. Never ask to expose it.
- Mutating tools need `confirm: true`.
- Prefer **batch + modular schematics** over placing every block individually.
- Prefer compact `world_scan` over `detail=blocks` unless the region is tiny.
- Prefer `world_players` over entity scans when locating a builder.

## Region tips

- Center form: `world`, `x`, `y`, `z`, `radius` (+ optional `radius_y`)
- Box form: `x1,y1,z1,x2,y2,z2`
- Defaults are set in plugin config (`limits.max_edge`, `limits.max_volume`)

## Example: pad near spawn

1. `world_list` → note spawn
2. `world_scan` radius 16 around spawn → find empty-ish volume
3. `world_marker` at pad center
4. `world_snapshot` the pad AABB
5. `world_fill` stone pad (`confirm: true`)
6. `world_diff` / `world_assert_materials` to confirm
