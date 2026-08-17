package dev.terrorify.tools.mcpclient.mcp.tools;

import com.google.gson.JsonObject;
import dev.terrorify.tools.mcpclient.mcp.McpTool;
import dev.terrorify.tools.mcpclient.mcp.ToolException;
import dev.terrorify.tools.mcpclient.world.WorldProbe;

public final class GetAreaTool implements McpTool {
    @Override
    public String name() {
        return "get_area";
    }

    @Override
    public String description() {
        return "Read block states in a box [min, max] (inclusive) from the client's own chunk cache. Capped at "
            + dev.terrorify.tools.mcpclient.world.WorldProbe.MAX_VOLUME + " blocks (16x16x16) to stay context-cheap — prefer small boxes near the point of interest.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject point = new JsonObject();
        point.addProperty("type", "array");
        props.add("min", point);
        props.add("max", point);
        schema.add("properties", props);
        com.google.gson.JsonArray required = new com.google.gson.JsonArray();
        required.add("min");
        required.add("max");
        schema.add("required", required);
        return schema;
    }

    @Override
    public JsonObject call(JsonObject args) throws ToolException {
        if (!args.has("min") || !args.has("max")) {
            throw new ToolException("Missing required args: min [x,y,z], max [x,y,z]");
        }
        var min = args.getAsJsonArray("min");
        var max = args.getAsJsonArray("max");
        if (min.size() != 3 || max.size() != 3) {
            throw new ToolException("min/max must each be a 3-element [x,y,z] array");
        }
        return WorldProbe.getArea(
            min.get(0).getAsInt(), min.get(1).getAsInt(), min.get(2).getAsInt(),
            max.get(0).getAsInt(), max.get(1).getAsInt(), max.get(2).getAsInt()
        );
    }
}
