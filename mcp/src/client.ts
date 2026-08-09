export type HttpClientOptions = {
  baseUrl: string;
  token: string;
};

export class WorldAgentClient {
  constructor(private readonly opts: HttpClientOptions) {}

  async get(path: string, query?: Record<string, string | number | boolean | undefined>) {
    const url = new URL(path, this.opts.baseUrl.endsWith("/") ? this.opts.baseUrl : this.opts.baseUrl + "/");
    if (query) {
      for (const [k, v] of Object.entries(query)) {
        if (v !== undefined && v !== null && v !== "") {
          url.searchParams.set(k, String(v));
        }
      }
    }
    return this.request(url, { method: "GET" });
  }

  async post(path: string, body: Record<string, unknown>) {
    const url = new URL(path, this.opts.baseUrl.endsWith("/") ? this.opts.baseUrl : this.opts.baseUrl + "/");
    return this.request(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  }

  private async request(url: URL, init: RequestInit) {
    const headers = new Headers(init.headers);
    headers.set("Authorization", `Bearer ${this.opts.token}`);
    const res = await fetch(url, { ...init, headers });
    const text = await res.text();
    let json: unknown;
    try {
      json = text ? JSON.parse(text) : {};
    } catch {
      json = { ok: false, error: text };
    }
    if (!res.ok) {
      const err = typeof json === "object" && json && "error" in json
        ? String((json as { error: unknown }).error)
        : res.statusText;
      throw new Error(`HTTP ${res.status}: ${err}`);
    }
    return json;
  }
}
