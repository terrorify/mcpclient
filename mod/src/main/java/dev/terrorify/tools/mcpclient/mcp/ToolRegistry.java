package dev.terrorify.tools.mcpclient.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Holds every registered {@link McpTool} and marshals calls onto the
 * Minecraft client thread — HTTP handling happens on its own thread pool
 * (see {@link McpHttpServer}), but game state is only safe to touch from
 * the main client thread.
 */
public final class ToolRegistry {
    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    public void register(McpTool tool) {
        tools.put(tool.name(), tool);
    }

    public JsonArray listToolsJson() {
        JsonArray array = new JsonArray();
        for (McpTool tool : tools.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", tool.name());
            entry.addProperty("description", tool.description());
            entry.add("inputSchema", tool.inputSchema());
            array.add(entry);
        }
        return array;
    }

    /** Runs {@code toolName}'s {@link McpTool#call} on the client thread and waits for the result. */
    public JsonObject call(String toolName, JsonObject args) throws ToolException {
        McpTool tool = tools.get(toolName);
        if (tool == null) {
            throw new ToolException("Unknown tool: " + toolName);
        }

        JsonObject safeArgs = args == null ? new JsonObject() : args;
        java.util.concurrent.CompletableFuture<JsonObject> future = Minecraft.getInstance().submit(() -> {
            try {
                return tool.call(safeArgs);
            } catch (ToolException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        });
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.util.concurrent.CompletionException completion) {
                cause = completion.getCause();
            }
            if (cause instanceof ToolException toolException) {
                throw toolException;
            }
            throw new ToolException("Tool '" + toolName + "' failed: " + cause, cause);
        } catch (TimeoutException e) {
            throw new ToolException("Tool '" + toolName + "' timed out after 10s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolException("Tool '" + toolName + "' interrupted");
        }
    }
}
