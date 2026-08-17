package dev.terrorify.tools.mcpclient.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.terrorify.tools.mcpclient.mcp.McpTool;
import dev.terrorify.tools.mcpclient.mcp.ToolException;
import dev.terrorify.tools.mcpclient.telemetry.TelemetryBuffer;
import dev.terrorify.tools.mcpclient.telemetry.TickRecord;

import java.util.List;

public final class GetTicksTool implements McpTool {
    private final TelemetryBuffer telemetry;

    public GetTicksTool(TelemetryBuffer telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public String name() {
        return "get_ticks";
    }

    @Override
    public String description() {
        return "Fetch buffered TickRecords in [from, to] (inclusive). Omit both for the last 100 ticks. "
            + "Capped at " + MAX_SPAN + " ticks/call to stay context-cheap — for bulk/offline analysis use "
            + "capture_start + capture_stop and read the JSONL files directly instead of paging this tool.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject from = new JsonObject();
        from.addProperty("type", "integer");
        props.add("from", from);
        JsonObject to = new JsonObject();
        to.addProperty("type", "integer");
        props.add("to", to);
        schema.add("properties", props);
        return schema;
    }

    private static final int MAX_SPAN = 300;
    private static final int DEFAULT_WINDOW = 100;

    @Override
    public JsonObject call(JsonObject args) throws ToolException {
        int currentTick = telemetry.currentTick();
        int from = args.has("from") ? args.get("from").getAsInt() : Math.max(0, currentTick - DEFAULT_WINDOW);
        int to = args.has("to") ? args.get("to").getAsInt() : currentTick;
        boolean truncated = false;
        if (to - from + 1 > MAX_SPAN) {
            from = to - MAX_SPAN + 1;
            truncated = true;
        }

        List<TickRecord> records = telemetry.range(from, to);
        JsonArray array = new JsonArray();
        for (TickRecord r : records) {
            array.add(r.toJson());
        }

        JsonObject result = new JsonObject();
        result.add("ticks", array);
        result.addProperty("count", array.size());
        result.addProperty("from", from);
        result.addProperty("to", to);
        result.addProperty("truncated", truncated);
        return result;
    }
}
