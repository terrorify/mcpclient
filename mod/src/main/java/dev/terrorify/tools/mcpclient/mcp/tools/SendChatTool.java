package dev.terrorify.tools.mcpclient.mcp.tools;

import com.google.gson.JsonObject;
import dev.terrorify.tools.mcpclient.mcp.McpTool;
import dev.terrorify.tools.mcpclient.mcp.ToolException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class SendChatTool implements McpTool {
    @Override
    public String name() {
        return "send_chat";
    }

    @Override
    public String description() {
        return "Send a chat message or slash command as the connected player.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject text = new JsonObject();
        text.addProperty("type", "string");
        props.add("text", text);
        schema.add("properties", props);
        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("text");
        schema.add("required", required);
        return schema;
    }

    @Override
    public JsonObject call(JsonObject args) throws ToolException {
        if (!args.has("text")) {
            throw new ToolException("Missing required arg: text");
        }
        String text = args.get("text").getAsString();

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            throw new ToolException("Not connected: no player");
        }

        if (text.startsWith("/")) {
            player.connection.sendCommand(text.substring(1));
        } else {
            player.connection.sendChat(text);
        }

        JsonObject result = new JsonObject();
        result.addProperty("sent", true);
        return result;
    }
}
