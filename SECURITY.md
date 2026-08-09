# Security

Aelion World Agent is a localhost control plane for a live Minecraft world. Treat `http.token` like a root password for that server process.

## Expected deployment

- HTTP binds to `127.0.0.1` only
- Agents (MCP, scripts) run on the same machine as Paper
- Token lives in `config.yml` / local env only — never commit real tokens
- On first enable, blank or known-placeholder tokens are replaced with a generated secret

## What we will not support

- Binding to `0.0.0.0` / public interfaces in v1
- Unauthenticated mutation endpoints
- Shipping a known default token that works out of the box

## Reporting

If you find a vulnerability in this repository, open a private security advisory on the GitHub repo or contact the maintainers through [Aelion Solutions](https://github.com/Aelion-Solutions).
