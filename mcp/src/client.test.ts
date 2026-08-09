import assert from "node:assert/strict";
import { afterEach, mock, test } from "node:test";
import { WorldAgentClient } from "./client.js";

afterEach(() => {
  mock.restoreAll();
});

test("get sends Bearer token and query params", async () => {
  const calls: Array<{ url: string; init: RequestInit }> = [];
  mock.method(globalThis, "fetch", async (input: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ url: String(input), init: init ?? {} });
    return new Response(JSON.stringify({ ok: true }), { status: 200 });
  });

  const client = new WorldAgentClient({
    baseUrl: "http://127.0.0.1:8765",
    token: "secret-token",
  });
  const json = await client.get("/v1/scan", { world: "world", radius: 8 });
  assert.deepEqual(json, { ok: true });
  assert.equal(calls.length, 1);
  assert.match(calls[0].url, /\/v1\/scan\?world=world&radius=8$/);
  const headers = new Headers(calls[0].init.headers);
  assert.equal(headers.get("Authorization"), "Bearer secret-token");
});

test("post throws with HTTP status and error body", async () => {
  mock.method(globalThis, "fetch", async () =>
    new Response(JSON.stringify({ error: "Mutations disabled" }), { status: 403 }),
  );

  const client = new WorldAgentClient({
    baseUrl: "http://127.0.0.1:8765/",
    token: "secret-token",
  });
  await assert.rejects(
    () => client.post("/v1/fill", { world: "world", material: "stone" }),
    /HTTP 403: Mutations disabled/,
  );
});
