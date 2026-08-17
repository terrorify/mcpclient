package dev.terrorify.tools.mcpclient.mcp.tools;

import com.google.gson.JsonObject;
import dev.terrorify.tools.mcpclient.input.InputScriptEngine;
import dev.terrorify.tools.mcpclient.mcp.McpTool;
import dev.terrorify.tools.mcpclient.mcp.ToolException;

public final class StopTool implements McpTool {
    private final InputScriptEngine engine;

    public StopTool(InputScriptEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "stop";
    }

    @Override
    public String description() {
        return "Stop any running input script and release all held keys.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        return schema;
    }

    @Override
    public JsonObject call(JsonObject args) throws ToolException {
        engine.stop();
        JsonObject result = new JsonObject();
        result.addProperty("stopped", true);
        return result;
    }
}
