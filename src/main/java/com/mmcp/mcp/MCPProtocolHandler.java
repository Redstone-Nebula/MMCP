package com.mmcp.mcp;

import com.google.gson.*;
import com.mmcp.MMCPMod;
import com.mmcp.entity.AIEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

/**
 * MCP 协议处理器
 * 处理 JSON-RPC 消息格式的请求和响应
 * 提供工具：get_server_info, list_players, send_chat_message,
 *          get_ai_status, move_ai, follow_player, stop_following
 */
public class MCPProtocolHandler {
    private final MCPServer server;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public MCPProtocolHandler(MCPServer server) {
        this.server = server;
    }

    /**
     * 处理 MCP 消息（JSON-RPC 格式）
     */
    public String handleMessage(String body) {
        try {
            JsonObject request = gson.fromJson(body, JsonObject.class);
            if (request == null || !request.has("method")) {
                return errorResponse(null, -32600, "Invalid Request: missing method");
            }

            String method = request.get("method").getAsString();
            String id = request.has("id") ? request.get("id").getAsString() : null;
            JsonObject params = request.has("params") ? request.getAsJsonObject("params") : new JsonObject();

            return switch (method) {
                case "get_server_info" -> handleGetServerInfo(id);
                case "list_players" -> handleListPlayers(id);
                case "send_chat_message" -> handleSendChatMessage(id, params);
                case "get_ai_status" -> handleGetAIStatus(id);
                case "list_ai_entities" -> handleListAIEntities(id);
                case "move_ai" -> handleMoveAI(id, params);
                case "follow_player" -> handleFollowPlayer(id, params);
                case "stop_following" -> handleStopFollowing(id, params);
                case "ping" -> successResponse(id, new JsonObject());
                default -> errorResponse(id, -32601, "Method not found: " + method);
            };
        } catch (JsonSyntaxException e) {
            return errorResponse(null, -32700, "Parse error: invalid JSON");
        } catch (Exception e) {
            MMCPMod.LOGGER.error("Error handling MCP message", e);
            return errorResponse(null, -32603, "Internal error: " + e.getMessage());
        }
    }

    // ==================== Handler 实现 ====================

    private String handleGetServerInfo(String id) {
        var mcServer = server.getMinecraftServer();
        JsonObject info = new JsonObject();
        info.addProperty("name", "MMCP AI Server");
        info.addProperty("version", "1.0.0");
        info.addProperty("motd", mcServer.getMotd());
        info.addProperty("players", mcServer.getPlayerCount());
        info.addProperty("maxPlayers", mcServer.getMaxPlayers());
        info.addProperty("port", server.getPort());
        info.addProperty("aiEntities", server.getAllAIEntities().size());
        return successResponse(id, info);
    }

    private String handleListPlayers(String id) {
        var mcServer = server.getMinecraftServer();
        JsonArray players = new JsonArray();
        for (Player player : mcServer.getPlayerList().getPlayers()) {
            JsonObject p = new JsonObject();
            p.addProperty("name", player.getName().getString());
            p.addProperty("uuid", player.getUUID().toString());
            p.addProperty("health", player.getHealth());
            p.addProperty("position", String.format("%.1f, %.1f, %.1f",
                    player.getX(), player.getY(), player.getZ()));
            players.add(p);
        }
        JsonObject result = new JsonObject();
        result.add("players", players);
        result.addProperty("count", players.size());
        return successResponse(id, result);
    }

    private String handleSendChatMessage(String id, JsonObject params) {
        String message = getStringParam(params, "message");
        if (message == null || message.isEmpty()) {
            return errorResponse(id, -32602, "Missing 'message' parameter");
        }

        List<AIEntity> ais = server.getAllAIEntities();
        if (ais.isEmpty()) {
            return errorResponse(id, -32000, "No AI entities found in the world");
        }

        for (AIEntity ai : ais) {
            ai.broadcastChat(message);
        }

        // 也广播给 MCP SSE 客户端
        server.broadcastChat("MCP Client", message);

        JsonObject result = new JsonObject();
        result.addProperty("sent", true);
        result.addProperty("entities", ais.size());
        return successResponse(id, result);
    }

    private String handleGetAIStatus(String id) {
        List<AIEntity> ais = server.getAllAIEntities();
        if (ais.isEmpty()) {
            return errorResponse(id, -32000, "No AI entities found in the world");
        }

        AIEntity ai = ais.get(0);
        JsonObject status = new JsonObject();
        status.addProperty("health", ai.getHealth());
        status.addProperty("maxHealth", ai.getMaxHealth());
        status.addProperty("healthPercent", ai.getHealthPercent());
        status.addProperty("position", String.format("%.1f, %.1f, %.1f",
                ai.getX(), ai.getY(), ai.getZ()));
        status.addProperty("dimension", ai.level().dimension().location().toString());
        status.addProperty("followingPlayer", ai.isFollowingPlayer() ?
                ai.getFollowingPlayer().getName().getString() : "none");
        status.addProperty("speed", ai.getSpeed());

        JsonArray nearby = new JsonArray();
        for (Player p : ai.getNearbyPlayers(16.0D)) {
            nearby.add(p.getName().getString());
        }
        status.add("nearbyPlayers", nearby);

        return successResponse(id, status);
    }

    private String handleListAIEntities(String id) {
        List<AIEntity> ais = server.getAllAIEntities();
        JsonArray entities = new JsonArray();
        for (AIEntity ai : ais) {
            JsonObject e = new JsonObject();
            e.addProperty("id", ai.getUUID().toString());
            e.addProperty("health", String.format("%.1f/%.1f", ai.getHealth(), ai.getMaxHealth()));
            e.addProperty("position", String.format("%.1f, %.1f, %.1f",
                    ai.getX(), ai.getY(), ai.getZ()));
            e.addProperty("dimension", ai.level().dimension().location().toString());
            e.addProperty("following", ai.isFollowingPlayer() ?
                    ai.getFollowingPlayer().getName().getString() : "none");
            entities.add(e);
        }
        JsonObject result = new JsonObject();
        result.add("entities", entities);
        result.addProperty("count", entities.size());
        return successResponse(id, result);
    }

    private String handleMoveAI(String id, JsonObject params) {
        double x = getDoubleParam(params, "x", Double.NaN);
        double y = getDoubleParam(params, "y", Double.NaN);
        double z = getDoubleParam(params, "z", Double.NaN);

        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            return errorResponse(id, -32602, "Missing coordinates: x, y, z required");
        }

        List<AIEntity> ais = server.getAllAIEntities();
        if (ais.isEmpty()) {
            return errorResponse(id, -32000, "No AI entities found");
        }

        AIEntity ai = ais.get(0);
        ai.moveToPosition(x, y, z);

        JsonObject result = new JsonObject();
        result.addProperty("moved", true);
        result.addProperty("target", String.format("%.1f, %.1f, %.1f", x, y, z));
        return successResponse(id, result);
    }

    private String handleFollowPlayer(String id, JsonObject params) {
        String playerName = getStringParam(params, "player");
        if (playerName == null || playerName.isEmpty()) {
            return errorResponse(id, -32602, "Missing 'player' parameter");
        }

        List<AIEntity> ais = server.getAllAIEntities();
        if (ais.isEmpty()) {
            return errorResponse(id, -32000, "No AI entities found");
        }

        Player target = server.getMinecraftServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            return errorResponse(id, -32001, "Player not found: " + playerName);
        }

        ais.get(0).followPlayer(target);

        JsonObject result = new JsonObject();
        result.addProperty("following", true);
        result.addProperty("player", playerName);
        return successResponse(id, result);
    }

    private String handleStopFollowing(String id, JsonObject params) {
        List<AIEntity> ais = server.getAllAIEntities();
        if (ais.isEmpty()) {
            return errorResponse(id, -32000, "No AI entities found");
        }

        ais.get(0).stopFollowing();

        JsonObject result = new JsonObject();
        result.addProperty("stopped", true);
        return successResponse(id, result);
    }

    // ==================== JSON-RPC 响应构造 ====================

    private String successResponse(String id, JsonObject result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id != null) response.addProperty("id", id);
        response.add("result", result);
        return gson.toJson(response);
    }

    private String errorResponse(String id, int code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        if (id != null) response.addProperty("id", id);
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);
        return gson.toJson(response);
    }

    // ==================== 参数提取 ====================

    private String getStringParam(JsonObject params, String key) {
        if (!params.has(key) || params.get(key).isJsonNull()) return null;
        return params.get(key).getAsString();
    }

    private double getDoubleParam(JsonObject params, String key, double defaultValue) {
        if (!params.has(key) || params.get(key).isJsonNull()) return defaultValue;
        return params.get(key).getAsDouble();
    }
}