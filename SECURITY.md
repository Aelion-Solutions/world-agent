# Security

Aelion World Agent is a **localhost control plane** for a live Minecraft world. Treat the bearer token like a root password for that server process.

## Expected deployment

- HTTP binds to `127.0.0.1` only
- Agents (Cursor MCP, scripts) run on the same machine as Paper
- Token is shared only via local env / `config.yml` — never commit real tokens

## What we will not support

- Binding to `0.0.0.0` / public interfaces in v1
- Unauthenticated mutation endpoints
- Shipping default tokens into production servers

## Reporting

If you find a vulnerability in this repository, open a private security advisory on the GitHub repo or contact the maintainers through [Aelion Solutions](https://github.com/Aelion-Solutions).
