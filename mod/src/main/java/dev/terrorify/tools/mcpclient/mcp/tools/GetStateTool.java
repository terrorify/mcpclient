package dev.terrorify.tools.mcpclient.mcp.tools;

import com.google.gson.JsonObject;
import dev.terrorify.tools.mcpclient.capture.CaptureWriter;
import dev.terrorify.tools.mcpclient.input.InputScriptEngine;
import dev.terrorify.tools.mcpclient.mcp.McpTool;
import dev.terrorify.tools.mcpclient.mcp.ToolException;
import dev.terrorify.tools.mcpclient.telemetry.TelemetryBuffer;
import dev.terrorify.tools.mcpclient.telemetry.TickRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class GetStateTool implements McpTool {
    private final TelemetryBuffer telemetry;
    private final InputScriptEngine engine;
    private final CaptureWriter captureWriter;

    public GetStateTool(TelemetryBuffer telemetry, InputScriptEngine engine, CaptureWriter captureWriter) {
        this.telemetry = telemetry;
        this.engine = engine;
        this.captureWriter = captureWriter;
    }

    @Override
    public String name() {
        return "get_state";
    }

    @Override
    public String description() {
        return "Current client status: connection, position, tick, capture/script state.";
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
        LocalPlayer player = mc.player;

        JsonObject result = new JsonObject();
        result.addProperty("connected", player != null && mc.level != null);
        result.addProperty("tick", telemetry.currentTick());
        result.addProperty("script_running", engine.isRunning());
        result.addProperty("capture_active", captureWriter.isActive());

        if (player != null && mc.level != null) {
            result.addProperty("x", player.getX());
            result.addProperty("y", player.getY());
            result.addProperty("z", player.getZ());
            result.addProperty("yaw", player.getYRot());
            result.addProperty("pitch", player.getXRot());
            result.addProperty("on_ground", player.onGround());
            result.addProperty("dimension", mc.level.dimension().identifier().toString());
        }

        TickRecord latest = telemetry.latest();
        if (latest != null) {
            result.add("latest_tick_record", latest.toJson());
        }

        return result;
    }
}
