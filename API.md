# MMCP-AI API 文档

MCP（Model Context Protocol）服务器基于 HTTP/SSE 协议，JSON-RPC 2.0 格式，监听端口 **8932**。

---

## 基础信息

| 项目 | 值 |
|------|-----|
| 协议 | HTTP + SSE |
| 消息格式 | JSON-RPC 2.0 |
| 端口 | 8932 |
| 基础 URL | `http://<服务器IP>:8932` |

### JSON-RPC 请求格式

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "<方法名>",
  "params": { }
}
```

### JSON-RPC 响应格式

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "result": { }
}
```

### 错误响应格式

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "error": {
    "code": -32601,
    "message": "Method not found"
  }
}
```

---

## 端点

### `GET /sse` — SSE 事件流

建立 SSE 长连接，实时接收服务器事件推送。

**响应头：**
- `Content-Type: text/event-stream`
- `Cache-Control: no-cache`
- `Connection: keep-alive`

**事件类型：**

| 事件 | 说明 | 频率 |
|------|------|------|
| `connected` | 初始连接确认，包含 sessionId | 连接建立时 |
| `ping` | 心跳保活 | 每 1 秒 |
| `chat` | 聊天消息广播 | 触发时 |

**示例：**

```json
// connected 事件
event: connected
data: {"sessionId":"sse-1","server":"MMCP AI"}

// ping 事件
event: ping
data: {}

// chat 事件
event: chat
data: {"type":"chat","player":"Steve","message":"Hello!"}
```

### `POST /message` — 发送指令

发送 JSON-RPC 请求并接收 JSON 响应。

**请求头：** `Content-Type: application/json`

**示例：**

```bash
curl -X POST http://localhost:8932/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"get_server_info","params":{}}'
```

### `GET /health` — 健康检查

快速检查服务器状态。

**响应：**

```json
{
  "status": "ok",
  "players": 3,
  "aiEntities": 1
}
```

### `GET /` — 服务器信息

返回服务器基本信息及可用端点列表。

**响应：**

```json
{
  "server": "MMCP AI MCP Server",
  "version": "1.0.0",
  "endpoints": {
    "sse": "/sse",
    "message": "/message (POST)",
    "health": "/health"
  }
}
```

---

## API 方法

### `get_server_info`

获取服务器信息。

**参数：** 无

**响应：**

```json
{
  "name": "MMCP AI Server",
  "version": "1.0.0",
  "motd": "A Minecraft Server",
  "players": 3,
  "maxPlayers": 20,
  "port": 8932,
  "aiEntities": 1
}
```

**错误码：** 无

---

### `list_players`

列出所有在线玩家。

**参数：** 无

**响应：**

```json
{
  "count": 2,
  "players": [
    {
      "name": "Steve",
      "uuid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "health": 20.0,
      "position": "100.5, 64.0, -200.3"
    },
    {
      "name": "Alex",
      "uuid": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
      "health": 18.5,
      "position": "105.2, 63.0, -198.7"
    }
  ]
}
```

**错误码：** 无

---

### `send_chat_message`

让 AI 实体发送聊天消息。

**参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `message` | string | 是 | 聊天消息内容 |

**请求：**

```json
{
  "message": "Hello everyone!"
}
```

**响应：**

```json
{
  "sent": true,
  "entities": 1
}
```

**错误码：**

| 代码 | 说明 |
|------|------|
| -32602 | 缺少 `message` 参数 |
| -32000 | 世界中没有 AI 实体 |

---

### `get_ai_status`

获取 AI 实体的详细状态。

**参数：** 无

**响应：**

```json
{
  "health": 40.0,
  "maxHealth": 40.0,
  "healthPercent": 100.0,
  "position": "100.5, 64.0, -200.3",
  "dimension": "minecraft:overworld",
  "followingPlayer": "Steve",
  "speed": 0.3,
  "nearbyPlayers": ["Steve", "Alex"]
}
```

**错误码：**

| 代码 | 说明 |
|------|------|
| -32000 | 世界中没有 AI 实体 |

---

### `list_ai_entities`

列出所有 AI 实体。

**参数：** 无

**响应：**

```json
{
  "count": 2,
  "entities": [
    {
      "id": "uuid-1111-2222",
      "health": "40.0/40.0",
      "position": "100.5, 64.0, -200.3",
      "dimension": "minecraft:overworld",
      "following": "Steve"
    },
    {
      "id": "uuid-3333-4444",
      "health": "35.0/40.0",
      "position": "150.2, 63.0, -150.7",
      "dimension": "minecraft:overworld",
      "following": "none"
    }
  ]
}
```

**错误码：** 无

---

### `move_ai`

命令 AI 实体移动到指定坐标。

**参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `x` | number | 是 | X 坐标 |
| `y` | number | 是 | Y 坐标 |
| `z` | number | 是 | Z 坐标 |

**请求：**

```json
{
  "x": 120.5,
  "y": 64.0,
  "z": -180.3
}
```

**响应：**

```json
{
  "moved": true,
  "target": "120.5, 64.0, -180.3"
}
```

**错误码：**

| 代码 | 说明 |
|------|------|
| -32602 | 缺少坐标参数 |
| -32000 | 世界中没有 AI 实体 |

---

### `follow_player`

命令 AI 实体跟随指定玩家。

**参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `player` | string | 是 | 玩家名称 |

**请求：**

```json
{
  "player": "Steve"
}
```

**响应：**

```json
{
  "following": true,
  "player": "Steve"
}
```

**错误码：**

| 代码 | 说明 |
|------|------|
| -32602 | 缺少 `player` 参数 |
| -32000 | 世界中没有 AI 实体 |
| -32001 | 玩家未找到 |

---

### `stop_following`

命令 AI 实体停止跟随当前玩家。

**参数：** 无

**响应：**

```json
{
  "stopped": true
}
```

**错误码：**

| 代码 | 说明 |
|------|------|
| -32000 | 世界中没有 AI 实体 |

---

### `ping`

检查连接是否正常。

**参数：** 无

**响应：**

```json
{}
```

---

## 错误码汇总

| 代码 | 说明 |
|------|------|
| -32700 | JSON 解析错误 |
| -32600 | 无效请求 |
| -32601 | 方法不存在 |
| -32602 | 无效参数 |
| -32603 | 内部错误 |
| -32000 | 世界中没有 AI 实体 |
| -32001 | 玩家未找到 |

---

## 完整使用示例

### 获取服务器信息

```bash
curl -X POST http://localhost:8932/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"get_server_info","params":{}}'
```

### 命令 AI 移动

```bash
curl -X POST http://localhost:8932/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"2","method":"move_ai","params":{"x":100,"y":64,"z":-200}}'
```

### 命令 AI 跟随玩家

```bash
curl -X POST http://localhost:8932/message \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"3","method":"follow_player","params":{"player":"Steve"}}'
```

### 监听 SSE 事件流

```bash
curl -N http://localhost:8932/sse
```

### Python MCP 客户端

```python
import requests
import json

BASE_URL = "http://localhost:8932"

def mcp_request(method, params=None):
    payload = {
        "jsonrpc": "2.0",
        "id": "1",
        "method": method,
        "params": params or {}
    }
    resp = requests.post(f"{BASE_URL}/message", json=payload)
    return resp.json()

# 获取服务器信息
info = mcp_request("get_server_info")
print(info["result"])

# 获取 AI 状态
status = mcp_request("get_ai_status")
print(status["result"])

# 命令 AI 移动
mcp_request("move_ai", {"x": 100, "y": 64, "z": -200})
```

### JavaScript MCP 客户端

```javascript
const BASE_URL = "http://localhost:8932";

async function mcpRequest(method, params = {}) {
  const resp = await fetch(`${BASE_URL}/message`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ jsonrpc: "2.0", id: "1", method, params })
  });
  return resp.json();
}

// 获取服务器信息
const info = await mcpRequest("get_server_info");
console.log(info.result);
```