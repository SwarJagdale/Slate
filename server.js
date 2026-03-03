import express from "express";
import helmet from "helmet";
import rateLimit from "express-rate-limit";
import { WebSocketServer } from "ws";
import { randomUUID, timingSafeEqual, createHash } from "crypto";
import { spawn } from "child_process";
import { readFileSync, readdirSync, createWriteStream, statSync } from "fs";
import path from "path";
import process from "process";

const PORT = Number(process.env.PORT || 3000);
const [APP_SERVER_CMD, ...APP_SERVER_CMD_EXTRA] = (process.env.CODEX_APP_SERVER_CMD || "codex app-server").split(" ").filter(Boolean);
const APP_SERVER_ARGS = [...APP_SERVER_CMD_EXTRA, ...(process.env.CODEX_APP_SERVER_ARGS || "").split(" ").filter(Boolean)];
const WORKSPACE_ROOT = path.resolve(process.env.WORKSPACE_ROOT || process.cwd());
const AUTH_TOKEN = process.env.WEBAPP_AUTH_TOKEN || "";
const MAX_MSG_BYTES = Number(process.env.MAX_MSG_BYTES || 256_000);
const MAX_QUEUE = Number(process.env.MAX_QUEUE || 200);
const KILL_IDLE_MS = Number(process.env.KILL_IDLE_MS || 10 * 60 * 1000);

const ENABLE_SESSION_LOG =
  process.env.SESSION_LOG === "1" ||
  process.env.WEBAPP_SESSION_LOG === "1";
const LOG_FILE = new URL("./session.log", import.meta.url).pathname;
const logStream = ENABLE_SESSION_LOG ? createWriteStream(LOG_FILE, { flags: "a" }) : null;

const WS_TICKET_TTL_MS = Number(process.env.WS_TICKET_TTL_MS || 2 * 60 * 1000);
const wsTickets = new Map(); // sid -> expiresAt (ms)

setInterval(() => {
  const now = Date.now();
  for (const [sid, exp] of wsTickets.entries()) {
    if (typeof exp !== "number" || exp <= now) wsTickets.delete(sid);
  }
}, 60_000).unref();

function log(...args) {
  const line = new Date().toISOString() + " " + args.join(" ");
  console.log(line);
  if (logStream) logStream.write(line + "\n");
}

// Write every raw JSONL line from the app-server to the log so the schema
// can be inspected after a session.
function logAppServerLine(sessionId, direction, line) {
  if (!logStream) return;
  logStream.write(JSON.stringify({ t: Date.now(), session: sessionId, dir: direction, raw: line }) + "\n");
}

function constantTimeEq(a, b) {
  const aa = Buffer.from(a || "");
  const bb = Buffer.from(b || "");
  if (aa.length !== bb.length) return false;
  return timingSafeEqual(aa, bb);
}

function bearerFromAuthHeader(hdr) {
  const h = hdr || "";
  return h.startsWith("Bearer ") ? h.slice(7) : "";
}

function requireAuth(req, res, next) {
  if (!AUTH_TOKEN) return res.status(500).json({ error: "Server auth not configured" });
  const token = bearerFromAuthHeader(req.headers.authorization);
  if (!constantTimeEq(token, AUTH_TOKEN)) return res.status(401).json({ error: "Unauthorized" });
  next();
}

function safeJsonParse(line) {
  if (!line) return null;
  if (Buffer.byteLength(line, "utf8") > MAX_MSG_BYTES) return { __oversize__: true };
  try {
    const obj = JSON.parse(line);
    if (obj && typeof obj === "object") return obj;
    return null;
  } catch {
    return null;
  }
}

function toJsonLine(obj) {
  const s = JSON.stringify(obj);
  if (Buffer.byteLength(s, "utf8") > MAX_MSG_BYTES) throw new Error("Outbound message too large");
  return s + "\n";
}

function redactSecrets(obj) {
  if (!obj || typeof obj !== "object") return obj;
  const s = JSON.stringify(obj);
  const redacted = s.replaceAll(AUTH_TOKEN, "***");
  return JSON.parse(redacted);
}

function hashWorkspaceRoot(p) {
  return createHash("sha256").update(p).digest("hex").slice(0, 12);
}

const app = express();
app.use(express.json({ limit: "256kb" }));
app.use(helmet({ contentSecurityPolicy: false }));
app.use(
  rateLimit({
    windowMs: 60_000,
    limit: 120,
    standardHeaders: true,
    legacyHeaders: false
  })
);

app.get("/health", (req, res) => res.json({ ok: true, workspace: hashWorkspaceRoot(WORKSPACE_ROOT) }));

// Mint a short-lived ticket so browser clients can connect to WS without putting
// a long-lived bearer token in the query string.
app.post("/session", requireAuth, (req, res) => {
  const sid = randomUUID();
  const expiresAt = Date.now() + WS_TICKET_TTL_MS;
  wsTickets.set(sid, expiresAt);
  res.json({ sid, expiresAt });
});

// List immediate subdirectories of WORKSPACE_ROOT so the client can pick one.
// Requires the same Bearer token auth as WebSocket.
app.get("/workspaces", requireAuth, (req, res) => {
  try {
    const IGNORE = new Set(["node_modules", ".git", ".cache", ".npm", "__pycache__"]);
    const entries = readdirSync(WORKSPACE_ROOT, { withFileTypes: true });
    const dirs = entries
      .filter(e => e.isDirectory() && !e.name.startsWith(".") && !IGNORE.has(e.name))
      .sort((a, b) => a.name.localeCompare(b.name))
      .map(e => ({ name: e.name, path: path.join(WORKSPACE_ROOT, e.name) }));
    res.json({ base: { name: path.basename(WORKSPACE_ROOT), path: WORKSPACE_ROOT }, dirs });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

app.get("/", (req, res) => {
  const html = readFileSync(new URL("./web/index.html", import.meta.url), "utf8");
  res.setHeader("Content-Type", "text/html; charset=utf-8");
  res.send(html);
});

const server = app.listen(PORT, () => {
  log(`Listening on http://localhost:${PORT}`);
  log(`Workspace hash: ${hashWorkspaceRoot(WORKSPACE_ROOT)}`);
  log(`Auth token configured: ${AUTH_TOKEN ? "yes" : "NO — set WEBAPP_AUTH_TOKEN"}`);
  log(`App server command: ${APP_SERVER_CMD} ${APP_SERVER_ARGS.join(" ")}`);
  log(`Session log: ${ENABLE_SESSION_LOG ? LOG_FILE : "disabled (set SESSION_LOG=1)"}`);
});

const wss = new WebSocketServer({ noServer: true, maxPayload: MAX_MSG_BYTES });

server.on("upgrade", (req, socket, head) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  if (url.pathname !== "/ws") {
    log(`WS upgrade rejected: unknown path ${url.pathname}`);
    socket.destroy();
    return;
  }
  if (!AUTH_TOKEN) {
    log("WS upgrade rejected: WEBAPP_AUTH_TOKEN not set");
    socket.end("HTTP/1.1 500 Auth Not Configured\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
    return;
  }

  const sid = url.searchParams.get("sid") || "";
  let authed = false;
  if (sid) {
    const exp = wsTickets.get(sid);
    if (typeof exp === "number" && exp > Date.now()) authed = true;
    wsTickets.delete(sid);
  }
  if (!authed) {
    const headerToken = bearerFromAuthHeader(req.headers.authorization);
    const queryToken = url.searchParams.get("token") || "";
    const token = headerToken || queryToken;
    authed = constantTimeEq(token, AUTH_TOKEN);
  }
  if (!authed) {
    log("WS upgrade rejected: unauthorized");
    socket.end("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
    return;
  }

  // Optional workspace sub-directory (must stay inside WORKSPACE_ROOT)
  const wsParam = url.searchParams.get("workspace") || "";
  let sessionWorkspace = WORKSPACE_ROOT;
  if (wsParam) {
    const resolved = path.resolve(wsParam);
    const allowed = resolved === WORKSPACE_ROOT ||
      resolved.startsWith(WORKSPACE_ROOT + path.sep);
    if (!allowed) {
      log(`WS upgrade rejected: workspace outside root: ${wsParam}`);
      socket.end("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
      return;
    }
    sessionWorkspace = resolved;
  }

  // Validate the workspace directory actually exists on disk
  try {
    const st = statSync(sessionWorkspace);
    if (!st.isDirectory()) throw new Error('not a directory');
  } catch (e) {
    log(`WS upgrade rejected: workspace not found: ${sessionWorkspace} (${e.message})`);
    socket.end("HTTP/1.1 404 Workspace Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
    return;
  }

  log(`WS upgrade accepted (workspace: ${sessionWorkspace})`);
  wss.handleUpgrade(req, socket, head, (ws) => wss.emit("connection", ws, req, sessionWorkspace));
});

const sessions = new Map();

function spawnAppServer(workspace) {
  const child = spawn(APP_SERVER_CMD, APP_SERVER_ARGS, {
    stdio: ["pipe", "pipe", "pipe"],
    cwd: workspace,
    env: {
      ...process.env,
      CODEX_WORKSPACE_ROOT: workspace
    }
  });
  child.stdin.setDefaultEncoding("utf8");
  child.stdout.setEncoding("utf8");
  child.stderr.setEncoding("utf8");
  return child;
}

function createSession(ws, workspace) {
  const id = randomUUID();
  const child = spawnAppServer(workspace);
  let stdoutBuf = "";
  let stderrBuf = "";
  const outQueue = [];
  let closed = false;
  let lastActivity = Date.now();

  const idleTimer = setInterval(() => {
    if (Date.now() - lastActivity > KILL_IDLE_MS) {
      terminate("idle_timeout");
    }
  }, 15_000);

  function sendToClient(evt) {
    if (closed) return;
    const payload = JSON.stringify(evt);
    if (Buffer.byteLength(payload, "utf8") > MAX_MSG_BYTES) return;
    if (ws.readyState === ws.OPEN) ws.send(payload);
  }

  function flushQueue() {
    if (closed) return;
    while (outQueue.length && outQueue[0]) {
      const line = outQueue.shift();
      if (!line) continue;
      const ok = child.stdin.write(line);
      if (!ok) return;
    }
  }

  child.stdin.on("drain", flushQueue);

  function sendToAppServer(obj) {
    if (closed) return;
    lastActivity = Date.now();
    let line;
    try {
      line = toJsonLine(obj);
    } catch (e) {
      sendToClient({ type: "error", error: "client_message_too_large" });
      return;
    }
    if (outQueue.length > MAX_QUEUE) {
      sendToClient({ type: "error", error: "server_overloaded" });
      return;
    }
    logAppServerLine(id, "client→app", line.trimEnd());
    outQueue.push(line);
    flushQueue();
  }

  function terminate(reason) {
    if (closed) return;
    closed = true;
    log(`Session ${id} terminated: ${reason}`);
    clearInterval(idleTimer);
    try {
      child.kill("SIGTERM");
      setTimeout(() => {
        try {
          child.kill("SIGKILL");
        } catch { }
      }, 1500);
    } catch { }
    sessions.delete(id);
    sendToClient({ type: "session_closed", reason });
    try {
      ws.close();
    } catch { }
  }

  child.stdout.on("data", (chunk) => {
    lastActivity = Date.now();
    stdoutBuf += chunk;
    let idx;
    while ((idx = stdoutBuf.indexOf("\n")) >= 0) {
      const line = stdoutBuf.slice(0, idx);
      stdoutBuf = stdoutBuf.slice(idx + 1);
      const obj = safeJsonParse(line);
      if (!obj) continue;
      logAppServerLine(id, "app→client", line);
      // console: skip high-frequency streaming lines to keep output readable
      const method = obj.method;
      const isStream = method === "item/agentMessage/delta" ||
        method === "item/reasoning/textDelta" ||
        method === "item/commandExecution/outputDelta";
      if (!isStream) log(`[appserver→] ${line.slice(0, 200)}`);
      if (obj.__oversize__) {
        sendToClient({ type: "error", error: "appserver_message_too_large" });
        continue;
      }
      sendToClient({ type: "event", data: redactSecrets(obj) });
    }
  });

  child.stderr.on("data", (chunk) => {
    lastActivity = Date.now();
    stderrBuf += chunk;
    if (stderrBuf.length > 8192) stderrBuf = stderrBuf.slice(-8192);
    sendToClient({ type: "stderr", data: stderrBuf });
  });

  child.on("exit", (code, signal) => {
    terminate(`appserver_exit:${code ?? ""}:${signal ?? ""}`.replaceAll("::", ":"));
  });

  sessions.set(id, { id, sendToAppServer, terminate });
  log(`Session ${id} created workspace=${workspace} (active: ${sessions.size})`);
  sendToClient({
    type: "session_ready",
    sessionId: id,
    workspace: hashWorkspaceRoot(workspace),
    workspaceName: path.basename(workspace),
  });
  return sessions.get(id);
}

function isAllowedClientMessage(msg) {
  if (!msg || typeof msg !== "object") return false;
  if (typeof msg.type !== "string") return false;
  if (msg.type === "rpc" && msg.rpc && typeof msg.rpc === "object") return true;
  if (msg.type === "permission" && msg.response && typeof msg.response === "object") return true;
  if (msg.type === "close") return true;
  return false;
}

wss.on("connection", (ws, req, sessionWorkspace) => {
  const session = createSession(ws, sessionWorkspace || WORKSPACE_ROOT);

  ws.on("message", (data) => {
    if (typeof data !== "string" && !(data instanceof Buffer)) return;
    const text = data.toString("utf8");
    if (Buffer.byteLength(text, "utf8") > MAX_MSG_BYTES) {
      try {
        ws.send(JSON.stringify({ type: "error", error: "client_message_too_large" }));
      } catch { }
      return;
    }
    let msg;
    try {
      msg = JSON.parse(text);
    } catch {
      return;
    }
    if (!isAllowedClientMessage(msg)) return;

    if (msg.type === "close") {
      session.terminate("client_close");
      return;
    }

    if (msg.type === "rpc") {
      session.sendToAppServer(msg.rpc);
      return;
    }

    if (msg.type === "permission") {
      session.sendToAppServer(msg.response);
      return;
    }
  });

  ws.on("close", () => {
    try {
      session.terminate("ws_closed");
    } catch { }
  });

  ws.on("error", () => {
    try {
      session.terminate("ws_error");
    } catch { }
  });
});
