package dev.terrorify.tools.mcpclient.mcp;

import com.google.gson.JsonObject;

/**
 * One MCP tool. {@link #call(JsonObject)} always runs on the Minecraft client
 * thread (scheduled by {@link ToolRegistry}) — implementations may touch
 * {@code Minecraft.getInstance()} state directly and safely.
 */
public interface McpTool {
    String name();

    String description();

    /** JSON Schema (draft 7 subset) describing the tool's input object. */
    JsonObject inputSchema();

    /** Perform the tool action; return a JSON-serializable result object. */
    JsonObject call(JsonObject args) throws ToolException;
}
