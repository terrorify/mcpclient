package dev.terrorify.tools.mcpclient.mcp.tools;

import com.google.gson.JsonObject;
import dev.terrorify.tools.mcpclient.mcp.McpTool;
import dev.terrorify.tools.mcpclient.mcp.ToolException;
import net.minecraft.client.Minecraft;

public final class DisconnectTool implements McpTool {
    @Override
    public String name() {
        return "disconnect";
    }

    @Override
    public String description() {
        return "Disconnect the client from its current server.";
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null && mc.player == null) {
            throw new ToolException("Not connected");
        }
        mc.disconnectWithProgressScreen();
        JsonObject result = new JsonObject();
        result.addProperty("disconnected", true);
        return result;
    }
}
