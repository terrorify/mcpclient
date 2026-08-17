package dev.terrorify.tools.mcpclient.mcp.tools;

import com.google.gson.JsonObject;
import dev.terrorify.tools.mcpclient.mcp.McpTool;
import dev.terrorify.tools.mcpclient.mcp.ToolException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class ConnectTool implements McpTool {
    @Override
    public String name() {
        return "connect";
    }

    @Override
    public String description() {
        return "Connect the client to a Minecraft server. Fails if already connected.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject host = new JsonObject();
        host.addProperty("type", "string");
        props.add("host", host);
        JsonObject port = new JsonObject();
        port.addProperty("type", "integer");
        props.add("port", port);
        schema.add("properties", props);
        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("host");
        schema.add("required", required);
        return schema;
    }

    @Override
    public JsonObject call(JsonObject args) throws ToolException {
        if (!args.has("host")) {
            throw new ToolException("Missing required arg: host");
        }
        String host = args.get("host").getAsString();
        int port = args.has("port") ? args.get("port").getAsInt() : 25565;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null || mc.player != null) {
            throw new ToolException("Already connected; call disconnect first");
        }

        ServerAddress address = new ServerAddress(host, port);
        ServerData serverData = new ServerData(host + ":" + port, host + ":" + port, ServerData.Type.OTHER);

        mc.setScreen(new TitleScreen());
        ConnectScreen.startConnecting(mc.screen, mc, address, serverData, false, null);

        JsonObject result = new JsonObject();
        result.addProperty("connecting", true);
        result.addProperty("host", host);
        result.addProperty("port", port);
        return result;
    }
}
