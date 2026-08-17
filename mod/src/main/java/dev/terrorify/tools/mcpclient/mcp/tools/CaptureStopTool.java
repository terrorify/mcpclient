package dev.terrorify.tools.mcpclient.mcp.tools;

import com.google.gson.JsonObject;
import dev.terrorify.tools.mcpclient.capture.CaptureWriter;
import dev.terrorify.tools.mcpclient.mcp.McpTool;
import dev.terrorify.tools.mcpclient.mcp.ToolException;

public final class CaptureStopTool implements McpTool {
    private final CaptureWriter captureWriter;

    public CaptureStopTool(CaptureWriter captureWriter) {
        this.captureWriter = captureWriter;
    }

    @Override
    public String name() {
        return "capture_stop";
    }

    @Override
    public String description() {
        return "Stop the running capture and flush all JSONL streams to disk.";
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
        String dir = captureWriter.stop();
        JsonObject result = new JsonObject();
        result.addProperty("stopped", true);
        result.addProperty("dir", dir);
        return result;
    }
}
