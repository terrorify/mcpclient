package dev.terrorify.tools.mcpclient.mcp.tools;

import com.google.gson.JsonObject;
import dev.terrorify.tools.mcpclient.mcp.McpTool;
import dev.terrorify.tools.mcpclient.mcp.ToolException;
import dev.terrorify.tools.mcpclient.world.WorldProbe;

public final class GetBlockTool implements McpTool {
    @Override
    public String name() {
        return "get_block";
    }

    @Override
    public String description() {
        return "Read the block state at (x, y, z) from the client's own chunk cache.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        for (String axis : new String[] {"x", "y", "z"}) {
            JsonObject coord = new JsonObject();
            coord.addProperty("type", "integer");
            props.add(axis, coord);
        }
        schema.add("properties", props);
        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("x");
        required.add("y");
        required.add("z");
        schema.add("required", required);
        return schema;
    }

    @Override
    public JsonObject call(JsonObject args) throws ToolException {
        if (!args.has("x") || !args.has("y") || !args.has("z")) {
            throw new ToolException("Missing required args: x, y, z");
        }
        return WorldProbe.getBlock(args.get("x").getAsInt(), args.get("y").getAsInt(), args.get("z").getAsInt());
    }
}
