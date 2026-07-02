export interface Env {
  TRACKTIME_RELAY: DurableObjectNamespace;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const id = env.TRACKTIME_RELAY.idFromName("singleton");
    const stub = env.TRACKTIME_RELAY.get(id);
    return stub.fetch(request);
  }
};

export class TrackTimeRelay implements DurableObject {
  private judgeSocket: WebSocket | null = null;
  private readonly pendingSync = new Map<
    string,
    { resolve: (value: Response) => void; reject: (reason?: unknown) => void }
  >();

  constructor(
    private readonly state: DurableObjectState,
    private readonly env: Env
  ) {}

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === "/ws") {
      if (request.headers.get("Upgrade") !== "websocket") {
        return new Response("Expected WebSocket upgrade", { status: 426 });
      }
      return this.handleWebSocket(request);
    }

    if (url.pathname === "/api/sync" && request.method === "POST") {
      return this.handleSync(request);
    }

    if (url.pathname === "/api/start" && request.method === "POST") {
      return this.handleStart(request);
    }

    return jsonResponse({ ok: true, message: "SprintMark relay is running" });
  }

  private handleWebSocket(request: Request): Response {
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    server.accept();

    server.addEventListener("message", (event) => {
      const payload = safeParse(event.data);
      if (!payload) return;

      if (payload.type === "register" && payload.role === "judge") {
        this.judgeSocket = server;
        server.send(JSON.stringify({ type: "registered", role: "judge" }));
        return;
      }

      if (payload.type === "sync-response") {
        const pending = this.pendingSync.get(payload.requestId);
        if (pending) {
          this.pendingSync.delete(payload.requestId);
          pending.resolve(
            jsonResponse({
              requestId: payload.requestId,
              t2JudgeReceiveMs: payload.t2JudgeReceiveMs,
              t3JudgeSendMs: payload.t3JudgeSendMs
            })
          );
        }
        return;
      }
    });

    server.addEventListener("close", () => {
      if (this.judgeSocket === server) {
        this.judgeSocket = null;
      }
    });

    return new Response(null, { status: 101, webSocket: client });
  }

  private async handleSync(request: Request): Promise<Response> {
    const judge = this.judgeSocket;
    if (!judge) {
      return jsonResponse({ error: "judge-not-connected" }, 503);
    }

    const payload = (await request.json()) as {
      requestId: string;
      t1ClientSendMs: number;
    };
    const requestId = payload.requestId;

    const promise = new Promise<Response>((resolve, reject) => {
      this.pendingSync.set(requestId, { resolve, reject });
      setTimeout(() => {
        if (this.pendingSync.has(requestId)) {
          this.pendingSync.delete(requestId);
          reject(new Error("sync timeout"));
        }
      }, 5000);
    });

    try {
      judge.send(
        JSON.stringify({
          type: "sync-request",
          requestId,
          t1ClientSendMs: payload.t1ClientSendMs
        })
      );
    } catch {
      this.pendingSync.delete(requestId);
      return jsonResponse({ error: "judge-send-failed" }, 503);
    }

    return promise.catch(() => jsonResponse({ error: "sync-timeout" }, 504));
  }

  private async handleStart(request: Request): Promise<Response> {
    const judge = this.judgeSocket;
    if (!judge) {
      return jsonResponse({ error: "judge-not-connected" }, 503);
    }

    const payload = (await request.json()) as {
      raceId: string;
      gunTimeMs: number;
      setLeadMs: number;
      setTimeMs: number;
    };

    try {
      judge.send(
        JSON.stringify({
          type: "start-schedule",
          raceId: payload.raceId,
          gunTimeMs: payload.gunTimeMs,
          setLeadMs: payload.setLeadMs,
          setTimeMs: payload.setTimeMs
        })
      );
    } catch {
      return jsonResponse({ error: "judge-send-failed" }, 503);
    }

    return jsonResponse({ ok: true });
  }
}

function safeParse(value: unknown): any | null {
  if (typeof value !== "string") return null;
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" }
  });
}
