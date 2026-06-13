package com.mmcp.mcp;

import com.mmcp.MMCPMod;
import com.mmcp.entity.AIEntity;
import com.mmcp.entity.ModEntities;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP (Model Context Protocol) HTTP/SSE 服务器
 * 监听端口 8932，提供 SSE 事件流和消息接口
 */
public class MCPServer {
    private final int port;
    private final net.minecraft.server.MinecraftServer minecraftServer;
    private HttpServer server;
    private final MCPProtocolHandler protocolHandler;
    private final List<SseClient> sseClients = new CopyOnWriteArrayList<>();
    private final AtomicInteger sessionCounter = new AtomicInteger(0);
    private boolean running = false;

    public MCPServer(int port, net.minecraft.server.MinecraftServer server) {
        this.port = port;
        this.minecraftServer = server;
        this.protocolHandler = new MCPProtocolHandler(this);
    }

    public void start() {
        if (running) return;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/sse", this::handleSse);
            server.createContext("/message", this::handleMessage);
            server.createContext("/health", this::handleHealth);
            server.createContext("/", this::handleRoot);
            server.setExecutor(Executors.newFixedThreadPool(8));
            server.start();
            running = true;
            MMCPMod.LOGGER.info("MCP Server listening on port {}", port);
        } catch (IOException e) {
            MMCPMod.LOGGER.error("Failed to start MCP Server", e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            running = false;
            for (SseClient client : sseClients) {
                client.close();
            }
            sseClients.clear();
            MMCPMod.LOGGER.info("MCP Server stopped");
        }
    }

    // ==================== HTTP Handlers ====================

    private void handleSse(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        String sessionId = "sse-" + sessionCounter.incrementAndGet();

        try {
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();

            // 发送初始连接事件
            String initEvent = "event: connected\ndata: {\"sessionId\":\"" + sessionId + "\",\"server\":\"MMCP AI\"}\n\n";
            out.write(initEvent.getBytes(StandardCharsets.UTF_8));
            out.flush();

            SseClient client = new SseClient(sessionId, out, exchange);
            sseClients.add(client);

            MMCPMod.LOGGER.info("SSE client connected: {}", sessionId);

            // 保持连接直到客户端断开
            while (!exchange.getHttpContext().getServer().getExecutor().isShutdown()) {
                try {
                    Thread.sleep(1000);
                    // 发送心跳
                    synchronized (client) {
                        if (client.isClosed()) break;
                        try {
                            out.write("event: ping\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8));
                            out.flush();
                        } catch (IOException e) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }

            // 发送结束事件: end of stream
        } catch (IOException e) {
            MMCPMod.LOGGER.debug("SSE client disconnected: {} - {}", sessionId, e.getMessage());
        } finally {
            sseClients.removeIf(c -> c.sessionId.equals(sessionId));
            exchange.close();
        }
    }

    private void handleMessage(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response = protocolHandler.handleMessage(body);
            sendJsonResponse(exchange, 200, response);
        } catch (IOException e) {
            sendJsonResponse(exchange, 400, "{\"error\":\"Invalid request body\"}");
        }
    }

    private void handleHealth(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        String health = String.format(
                "{\"status\":\"ok\",\"players\":%d,\"aiEntities\":%d}",
                minecraftServer.getPlayerCount(),
                countAIEntities()
        );
        sendJsonResponse(exchange, 200, health);
    }

    private void handleRoot(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        String info = String.format(
                "{\"server\":\"MMCP AI MCP Server\"," +
                        "\"version\":\"1.0.0\"," +
                        "\"endpoints\":{" +
                        "\"sse\":\"/sse\"," +
                        "\"message\":\"/message (POST)\"," +
                        "\"health\":\"/health\"" +
                        "}}"
        );
        sendJsonResponse(exchange, 200, info);
    }

    // ==================== 工具方法 ====================

    private void sendJsonResponse(HttpExchange exchange, int status, String json) {
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().flush();
        } catch (IOException e) {
            MMCPMod.LOGGER.error("Failed to send response", e);
        } finally {
            exchange.close();
        }
    }

    /**
     * 广播事件到所有 SSE 客户端
     */
    public void broadcastEvent(String eventType, String data) {
        String event = String.format("event: %s\ndata: %s\n\n", eventType, data);
        byte[] bytes = event.getBytes(StandardCharsets.UTF_8);

        for (SseClient client : sseClients) {
            synchronized (client) {
                if (client.isClosed()) continue;
                try {
                    client.output.write(bytes);
                    client.output.flush();
                } catch (IOException e) {
                    client.close();
                }
            }
        }
        sseClients.removeIf(SseClient::isClosed);
    }

    /**
     * 广播聊天消息到 SSE 客户端
     */
    public void broadcastChat(String playerName, String message) {
        String data = String.format(
                "{\"type\":\"chat\",\"player\":\"%s\",\"message\":\"%s\"}",
                escapeJson(playerName), escapeJson(message)
        );
        broadcastEvent("chat", data);
    }

    private int countAIEntities() {
        int count = 0;
        for (net.minecraft.server.level.ServerLevel level : minecraftServer.getAllLevels()) {
            for (AIEntity entity : level.getEntities().getAll().stream()
                    .filter(e -> e.getType() == ModEntities.AI_ENTITY.get())
                    .map(e -> (AIEntity) e)
                    .toList()) {
                count++;
            }
        }
        return count;
    }

    public AIEntity getFirstAIEntity() {
        for (net.minecraft.server.level.ServerLevel level : minecraftServer.getAllLevels()) {
            for (var entity : level.getEntities().getAll()) {
                if (entity.getType() == ModEntities.AI_ENTITY.get()) {
                    return (AIEntity) entity;
                }
            }
        }
        return null;
    }

    public List<AIEntity> getAllAIEntities() {
        return minecraftServer.getAllLevels().stream()
                .flatMap(level -> level.getEntities().getAll().stream())
                .filter(e -> e.getType() == ModEntities.AI_ENTITY.get())
                .map(e -> (AIEntity) e)
                .toList();
    }

    public net.minecraft.server.MinecraftServer getMinecraftServer() {
        return minecraftServer;
    }

    public int getPort() { return port; }
    public boolean isRunning() { return running; }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ==================== SSE 客户端 ====================

    private static class SseClient {
        final String sessionId;
        final OutputStream output;
        final HttpExchange exchange;
        private boolean closed = false;

        SseClient(String sessionId, OutputStream output, HttpExchange exchange) {
            this.sessionId = sessionId;
            this.output = output;
            this.exchange = exchange;
        }

        synchronized boolean isClosed() { return closed; }

        synchronized void close() {
            if (!closed) {
                closed = true;
                try { output.close(); } catch (IOException ignored) {}
                exchange.close();
            }
        }
    }
}