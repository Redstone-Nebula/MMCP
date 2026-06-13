# MMCP-AI

一个 NeoForge Minecraft 模组，为游戏添加具有自主行为的人工智能实体，并作为 MCP（Model Context Protocol）服务器广播。

## 功能

### AI 实体
- 40 点生命值（20 颗心），不会自然消失
- 默认 Steve 皮肤渲染
- 自主行为：随机漫步、四处张望、跳跃、粒子特效
- 玩家互动：跟随玩家、发送聊天消息、爱心粒子
- 伤害减免（单次伤害上限 6 点）

### MCP 服务器（端口 8932）

使用 SSE（Server-Sent Events）协议，提供以下工具：

| 方法 | 说明 |
|------|------|
| `get_server_info` | 服务器信息 |
| `list_players` | 列出玩家 |
| `send_chat_message` | AI 发送聊天消息 |
| `get_ai_status` | 获取 AI 状态 |
| `list_ai_entities` | 列出所有 AI |
| `move_ai` | 命令 AI 移动 |
| `follow_player` | 命令 AI 跟随玩家 |
| `stop_following` | 停止跟随 |

## 构建

### 方式一：GitHub Actions（推荐）

推送到 GitHub 后自动构建，从 Actions 页面下载 JAR：

```bash
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/你的用户名/你的仓库名.git
git push -u origin main
```

### 方式二：本地构建

需要 JDK 21 和正常运行的 Gradle 环境：

```bash
export JAVA_HOME=/path/to/jdk21
./gradlew build --no-daemon
```

构建产物：`build/libs/MMCP-AI-1.0.0.jar`

## 使用

1. 将 JAR 放入 Minecraft 的 `mods` 文件夹
2. 在创造模式物品栏中找到 "MMCP AI 刷怪蛋"
3. 生成 AI 实体后，MCP 服务器自动在端口 8932 启动
4. MCP 客户端连接：`http://<服务器IP>:8932/sse`
5. 发送请求：`POST http://<服务器IP>:8932/message`

### 示例请求

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "get_ai_status",
  "params": {}
}
```

## 技术栈

- Minecraft 1.21.1
- NeoForge 21.1.128
- NeoGradle 7.0.182
- Java 21