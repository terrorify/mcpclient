package dev.terrorify.tools.mcpclient.mcp.tools;

import com.google.gson.JsonObject;
import dev.terrorify.tools.mcpclient.capture.CaptureWriter;
import dev.terrorify.tools.mcpclient.mcp.McpTool;
import dev.terrorify.tools.mcpclient.mcp.ToolException;

public final class CaptureStartTool implements McpTool {
    private final CaptureWriter captureWriter;

    public CaptureStartTool(CaptureWriter captureWriter) {
        this.captureWriter = captureWriter;
    }

    @Override
    public String name() {
        return "capture_start";
    }

    @Override
    public String description() {
        return "Start writing ticks.jsonl/outbound.jsonl/inbound.jsonl under <game dir>/captures/<label>-<timestamp>/.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject label = new JsonObject();
        label.addProperty("type", "string");
        props.add("label", label);
        schema.add("properties", props);
        return schema;
    }

    @Override
    public JsonObject call(JsonObject args) throws ToolException {
        String label = args.has("label") ? args.get("label").getAsString() : "capture";
        String dir = captureWriter.start(label);
        JsonObject result = new JsonObject();
        result.addProperty("started", true);
        result.addProperty("dir", dir);
        return result;
    }
}
