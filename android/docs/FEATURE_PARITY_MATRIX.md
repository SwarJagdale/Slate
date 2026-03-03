# Web-to-Android Feature Parity Matrix

Reference: `web/index.html`, `server.js`. Last updated: 2025-02-28.

## 1. Connection & Auth

| Feature | Web | Android Target |
|---------|-----|----------------|
| Auth token input | `#tok` password field | SecureTextField / EncryptedSharedPreferences |
| Server host input | Uses `location.href` (same origin) | **Editable host + port** (user configurable) |
| Workspace picker | `#ws-sel` after token blur | Dropdown populated from `/workspaces` |
| Remember token | `#remember-tok` checkbox | Persist to EncryptedSharedPreferences |
| Connect button | `#conn-btn` | Primary button |
| Error display | `#conn-err` | Snackbar / inline error |
| Advanced section | Model, approval, sandbox in overlay | Expandable section |
| URL params | `?workspace=` | Deep link support |

## 2. HTTP API

| Endpoint | Method | Auth | Response | Android |
|----------|--------|------|----------|---------|
| `/workspaces` | GET | Bearer token | `{ base, dirs }` | OkHttp + Retrofit |
| `/health` | GET | none | `{ ok, workspace }` | Optional pre-check |

## 3. WebSocket Protocol

| Direction | Type | Payload | Android |
|-----------|------|---------|---------|
| Client→Server | `rpc` | `{ rpc: { id, method, params } }` | OkHttp WebSocket |
| Client→Server | `permission` | `{ response: { id, result: { decision } } }` | Same |
| Client→Server | `close` | `{}` | Same |
| Server→Client | `session_ready` | `{ sessionId, workspace, workspaceName }` | Parse, init |
| Server→Client | `event` | `{ data: <JSON-RPC> }` | Dispatch to handler |
| Server→Client | `stderr` | `{ data: string }` | Append to stderr row |
| Server→Client | `error` | `{ error: string }` | Show error |
| Server→Client | `session_closed` | `{ reason }` | Show system message |

**WS URL (web):** `ws://<host>:<port>/ws?sid=<ticket>&workspace=<path>`

**WS auth (android):** `Authorization: Bearer <token>` header (no token in query string)

## 4. RPC Methods (Client→Server)

| Method | Params | Android |
|--------|--------|---------|
| `initialize` | `{ clientInfo, capabilities }` | ✓ |
| `initialized` | `{}` (notification) | ✓ |
| `thread/start` | `{ model?, approvalPolicy?, sandbox? }` | ✓ |
| `thread/resume` | `{ threadId, model?, approvalPolicy?, sandbox? }` | ✓ |
| `thread/read` | `{ threadId, includeTurns: true }` | ✓ |
| `thread/list` | `{ limit, sortKey, archived?, cwd? }` | ✓ |
| `thread/archive` | `{ threadId }` | ✓ |
| `thread/unarchive` | `{ threadId }` | ✓ |
| `thread/fork` | `{ threadId }` | ✓ |
| `thread/rollback` | `{ threadId, turnCount }` | ✓ |
| `thread/compact/start` | `{ threadId }` | ✓ |
| `thread/backgroundTerminals/clean` | `{ threadId }` | ✓ |
| `thread/name/set` | `{ threadId, name }` | ✓ |
| `turn/start` | `{ threadId, input, model?, effort?, approvalPolicy?, sandboxPolicy?, collaborationMode? }` | ✓ |
| `turn/interrupt` | `{ threadId, turnId }` | ✓ |
| `model/list` | `{ limit, includeHidden }` | ✓ |
| `review/start` | `{ threadId, delivery, target }` | ✓ |
| `gitDiffToRemote` | `{ cwd }` | ✓ |
| `command/exec` | `{ command, sandboxPolicy }` | ✓ |
| `skills/list` | `{}` | ✓ |
| `mcpServerStatus/list` | `{}` | ✓ |
| `app/list` | `{ threadId? }` | ✓ |
| `experimentalFeature/list` | `{}` | ✓ |
| `account/logout` | - | ✓ |

## 5. Event Handlers (Server→Client)

| Method | Action | Android |
|--------|--------|---------|
| `error` | addError, setTurnIdle | ✓ |
| `turn/started` | setTurnActive, setStatus busy | ✓ |
| `turn/completed` | setTurnIdle, setStatus ok | ✓ |
| `turn/plan/updated` | updatePlan | ✓ |
| `turn/diff/updated` | add/update diff card | ✓ |
| `thread/tokenUsage/updated` | sbTokens, renderStatusBar | ✓ |
| `account/rateLimits/updated` | sbRLMap, renderStatusBar | ✓ |
| `item/started` | addThinkingItem, addAgentTextItem, addExecItem, addFileChangeItem, addMcpToolCallItem | ✓ |
| `item/completed` | collapseThinking, finalizeExec, finalizeAgent, finalizeMcpToolCall | ✓ |
| `item/agentMessage/delta` | addAgentDelta | ✓ |
| `item/reasoning/textDelta` | addThinkingDelta | ✓ |
| `item/reasoning/summaryTextDelta` | addThinkingDelta | ✓ |
| `item/commandExecution/outputDelta` | appendExecOutput | ✓ |
| Permission request (id+method+params) | addPermCard | ✓ |

## 6. Message Types (Chat UI)

| Type | Web Component | Android Component |
|------|----------------|-------------------|
| User bubble | `.bubble-user` | `UserMessageCard` |
| System pill | `.pill` | `SystemPill` |
| Agent label | `.agent-label` | `AgentLabel` |
| Agent markdown | `.msg-md` | `MarkdownText` (Compose) |
| Code block | `.msg-md pre code` + copy | `CodeBlock` + copy |
| Thinking block | `.thinking` collapsible | `ThinkingBlock` expandable |
| Exec card | `.exec-card` running/ok/fail | `ExecCard` |
| Tool card (MCP) | `.tool-card` | `ToolCallCard` |
| File diff card | `.file-card` | `FileChangeCard` |
| Permission card | `.perm-card` | `PermissionCard` |
| Plan card | `.plan-card` | `PlanCard` |
| Error row | `.err-row` | `ErrorRow` |
| Stderr row | `.stderr-row` | `StderrRow` |
| Event fallback | `.evt-card` | `EventCard` |

## 7. Slash Commands

| Command | Args | RPC/Action | Android |
|---------|------|------------|---------|
| `/new` | - | openNewChatModal | ✓ |
| `/threads` | - | toggleSidebar | ✓ |
| `/resume` | [id] | thread/resume or open history | ✓ |
| `/model` | [id] | set model or open settings | ✓ |
| `/interrupt` | - | turn/interrupt | ✓ |
| `/review` | [target] | review/start | ✓ |
| `/archive` | - | thread/archive | ✓ |
| `/unarchive` | id | thread/unarchive | ✓ |
| `/fork` | - | thread/fork → resume | ✓ |
| `/rollback` | [n] | thread/rollback → thread/read | ✓ |
| `/compact` | - | thread/compact/start | ✓ |
| `/diff` | - | gitDiffToRemote | ✓ |
| `/plan` | [prompt] | turn/start with plan mode | ✓ |
| `/skills` | - | skills/list | ✓ |
| `/mcp` | - | mcpServerStatus/list | ✓ |
| `/apps` | - | app/list | ✓ |
| `/experimental` | - | experimentalFeature/list | ✓ |
| `/status` | - | getSettings + sbTokens | ✓ |
| `/clearterminals` | - | thread/backgroundTerminals/clean | ✓ |
| `/run` | cmd | command/exec | ✓ |
| `/rename` | name | thread/name/set | ✓ |
| `/settings` | - | openSettings | ✓ |
| `/approval` | policy | saveSettings | ✓ |
| `/permissions` | - | alias for /approval | ✓ |
| `/sandbox` | mode | saveSettings | ✓ |
| `/logout` | - | account/logout | ✓ |
| `/quit`, `/exit` | - | close WS | ✓ |
| `/help` | - | list commands | ✓ |

## 8. Settings

| Key | Values | Storage | Android |
|-----|--------|---------|---------|
| model | string (model id) | localStorage | DataStore |
| effort | low, medium, high, '' | localStorage | DataStore |
| approvalPolicy | on-request, untrusted, never | localStorage | DataStore |
| sandbox | workspaceWrite, readOnly, dangerFullAccess | localStorage | DataStore |
| token | string (optional) | localStorage | EncryptedSharedPreferences |

## 9. State

| State | Web | Android |
|-------|-----|---------|
| ws | WebSocket | OkHttp WebSocket |
| threadId | string | StateFlow |
| activeTurnId | string? | StateFlow |
| sessionWorkspacePath | string? | StateFlow |
| rpcSeq | number | AtomicInt |
| pending | Map<id, promise> | ConcurrentHashMap + suspend |
| itemEls / itemTexts | Map | StateFlow / MutableStateMap |
| pendingMessages | Array | StateFlow<List> |
| threadListCache | Array | StateFlow<List> |
| sbTokens, sbRLMap | object, Map | StateFlow |

## 10. Diff Rendering

| Feature | Web | Android |
|---------|-----|---------|
| Parse unified diff | DiffRenderer.parseUnifiedLine | Kotlin parser |
| Line types | meta, hunk, add, del, ctx | Same |
| Stats | countStats (add, del) | Same |
| Syntax highlight | hljs from path | Optional (e.g. highlight.js port) |
| Badges | add, del, mod, rename | Same |

## 11. Mobile-Specific

| Requirement | Implementation |
|-------------|----------------|
| Server/port input | TextField host + port in ConnectScreen |
| Touch targets ≥ 48dp | Modifier.size(48.dp) for buttons |
| Adaptive layout | Drawer for history, BottomSheet for settings |
| Reconnect | Retry with exponential backoff |
| Process death | SavedStateHandle + ViewModel |
