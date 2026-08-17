package dev.terrorify.tools.mcpclient.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal MCP server over the streamable-HTTP transport (JSON-RPC 2.0 POST
 * to {@code /mcp}; no SSE — every response is a single JSON object, which is
 * sufficient for a synchronous request/response tool surface). Runs on its
 * own thread pool; tool execution is marshaled onto the client thread by
 * {@link ToolRegistry}.
 */
public final class McpHttpServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("mcpclient");
    private static final Gson GSON = new Gson();
    private static final String PROTOCOL_VERSION = "2025-06-18";

    private final ToolRegistry registry;
    private final String host;
    private final int port;
    private final String sessionId = UUID.randomUUID().toString();

    private HttpServer httpServer;
    private ExecutorService executor;

    public McpHttpServer(ToolRegistry registry, String host, int port) {
        this.registry = registry;
        this.host = host;
        this.port = port;
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        httpServer.createContext("/mcp", this::handle);
        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mcpclient-http");
            t.setDaemon(true);
            return t;
        });
        httpServer.setExecutor(executor);
        httpServer.start();
        LOGGER.info("mcpclient MCP server listening on http://{}:{}/mcp", host, port);
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public int port() {
        return port;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, errorEnvelope(null, -32600, "Only POST is supported"));
                return;
            }

            String body;
            try (InputStream in = exchange.getRequestBody()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            JsonObject request;
            try {
                request = JsonParser.parseString(body).getAsJsonObject();
            } catch (Exception e) {
                sendJson(exchange, 400, errorEnvelope(null, -32700, "Parse error: " + e.getMessage()));
                return;
            }

            JsonElement idElement = request.get("id");
            String methodName = request.has("method") ? request.get("method").getAsString() : "";

            if (methodName.startsWith("notifications/")) {
                exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
                exchange.sendResponseHeaders(202, -1);
                return;
            }

            JsonObject params = request.has("params") && request.get("params").isJsonObject()
                ? request.getAsJsonObject("params")
                : new JsonObject();

            JsonObject response = dispatch(methodName, params, idElement);
            exchange.getResponseHeaders().set("Mcp-Session-Id", sessionId);
            sendJson(exchange, 200, response);
        } catch (Exception e) {
            LOGGER.error("mcpclient MCP request failed", e);
            try {
                sendJson(exchange, 500, errorEnvelope(null, -32603, "Internal error: " + e.getMessage()));
            } catch (IOException ignored) {
                // exchange already closed
            }
        }
    }

    private JsonObject dispatch(String method, JsonObject params, JsonElement id) {
        try {
            return switch (method) {
                case "initialize" -> resultEnvelope(id, handleInitialize());
                case "ping" -> resultEnvelope(id, new JsonObject());
                case "tools/list" -> {
                    JsonObject result = new JsonObject();
                    result.add("tools", registry.listToolsJson());
                    yield resultEnvelope(id, result);
                }
                case "tools/call" -> resultEnvelope(id, handleToolsCall(params));
                default -> errorEnvelope(id, -32601, "Unknown method: " + method);
            };
        } catch (Exception e) {
            return errorEnvelope(id, -32603, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private JsonObject handleInitialize() {
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", PROTOCOL_VERSION);

        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());
        result.add("capabilities", capabilities);

        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "mcpclient");
        serverInfo.addProperty("version", "0.1.0");
        result.add("serverInfo", serverInfo);
        return result;
    }

    private JsonObject handleToolsCall(JsonObject params) {
        String name = params.has("name") ? params.get("name").getAsString() : null;
        JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject()
            ? params.getAsJsonObject("arguments")
            : new JsonObject();

        JsonObject result = new JsonObject();
        com.google.gson.JsonArray content = new com.google.gson.JsonArray();
        try {
            JsonObject toolResult = registry.call(name, arguments);
            JsonObject textBlock = new JsonObject();
            textBlock.addProperty("type", "text");
            textBlock.addProperty("text", GSON.toJson(toolResult));
            content.add(textBlock);
            result.add("content", content);
            result.addProperty("isError", false);
        } catch (ToolException e) {
            JsonObject textBlock = new JsonObject();
            textBlock.addProperty("type", "text");
            textBlock.addProperty("text", e.getMessage());
            content.add(textBlock);
            result.add("content", content);
            result.addProperty("isError", true);
        }
        return result;
    }

    private static JsonObject resultEnvelope(JsonElement id, JsonObject result) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("jsonrpc", "2.0");
        if (id != null) {
            envelope.add("id", id);
        }
        envelope.add("result", result);
        return envelope;
    }

    private static JsonObject errorEnvelope(JsonElement id, int code, String message) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("jsonrpc", "2.0");
        if (id != null) {
            envelope.add("id", id);
        }
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        envelope.add("error", error);
        return envelope;
    }

    private void sendJson(HttpExchange exchange, int status, JsonObject body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
