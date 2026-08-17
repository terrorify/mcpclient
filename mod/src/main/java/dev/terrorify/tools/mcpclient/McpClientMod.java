package dev.terrorify.tools.mcpclient;

import dev.terrorify.tools.mcpclient.capture.CaptureWriter;
import dev.terrorify.tools.mcpclient.input.InputScriptEngine;
import dev.terrorify.tools.mcpclient.mcp.McpHttpServer;
import dev.terrorify.tools.mcpclient.mcp.ToolRegistry;
import dev.terrorify.tools.mcpclient.mcp.tools.CaptureStartTool;
import dev.terrorify.tools.mcpclient.mcp.tools.CaptureStopTool;
import dev.terrorify.tools.mcpclient.mcp.tools.ConnectTool;
import dev.terrorify.tools.mcpclient.mcp.tools.DisconnectTool;
import dev.terrorify.tools.mcpclient.mcp.tools.GetAreaTool;
import dev.terrorify.tools.mcpclient.mcp.tools.GetBlockTool;
import dev.terrorify.tools.mcpclient.mcp.tools.GetStateTool;
import dev.terrorify.tools.mcpclient.mcp.tools.GetTicksTool;
import dev.terrorify.tools.mcpclient.mcp.tools.RunScriptTool;
import dev.terrorify.tools.mcpclient.mcp.tools.SendChatTool;
import dev.terrorify.tools.mcpclient.mcp.tools.StopTool;
import dev.terrorify.tools.mcpclient.spacetime.DebugSessionReporter;
import dev.terrorify.tools.mcpclient.telemetry.TelemetryBuffer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point. Wires the MCP HTTP server, telemetry sampling, and input
 * scripting to Fabric's client tick events.
 *
 * <p>Tick ordering is deliberate (see {@link CaptureWriter}): the input
 * script engine advances on START_CLIENT_TICK (before this tick's physics
 * consume key state); telemetry sampling + capture flush happen on
 * END_CLIENT_TICK (after physics + packet dispatch), so a TickRecord's
 * {@code input} always matches the {@code pos}/{@code vel} it produced.
 */
public final class McpClientMod implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("mcpclient");

    @Override
    public void onInitializeClient() {
        TelemetryBuffer telemetry = new TelemetryBuffer();
        InputScriptEngine scriptEngine = new InputScriptEngine();
        CaptureWriter captureWriter = new CaptureWriter(telemetry);
        DebugSessionReporter debugSession = new DebugSessionReporter();

        ToolRegistry registry = new ToolRegistry();
        registry.register(new GetStateTool(telemetry, scriptEngine, captureWriter));
        registry.register(new GetTicksTool(telemetry));
        registry.register(new GetBlockTool());
        registry.register(new GetAreaTool());
        registry.register(new ConnectTool());
        registry.register(new DisconnectTool());
        registry.register(new SendChatTool());
        registry.register(new RunScriptTool(scriptEngine));
        registry.register(new StopTool(scriptEngine));
        registry.register(new CaptureStartTool(captureWriter));
        registry.register(new CaptureStopTool(captureWriter));

        McpHttpServer server = new McpHttpServer(registry, McpConfig.HOST, McpConfig.PORT);
        try {
            server.start();
        } catch (Exception e) {
            LOGGER.error("mcpclient: failed to start MCP server on {}:{}", McpConfig.HOST, McpConfig.PORT, e);
        }

        ClientTickEvents.START_CLIENT_TICK.register(mc -> scriptEngine.tick());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            telemetry.sample();
            captureWriter.onTick();
            debugSession.onTick(telemetry);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> debugSession.onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> debugSession.onDisconnect());

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "mcpclient-shutdown"));

        LOGGER.info("mcpclient initialized: MCP server on http://{}:{}/mcp", McpConfig.HOST, McpConfig.PORT);
    }
}
