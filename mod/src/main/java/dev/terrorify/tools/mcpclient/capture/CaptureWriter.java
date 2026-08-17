package dev.terrorify.tools.mcpclient.capture;

import dev.terrorify.tools.mcpclient.mcp.ToolException;
import dev.terrorify.tools.mcpclient.net.NetTaps;
import dev.terrorify.tools.mcpclient.telemetry.TelemetryBuffer;
import dev.terrorify.tools.mcpclient.telemetry.TickRecord;
import net.minecraft.client.Minecraft;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Writes JSONL capture streams (ticks / outbound / inbound) under
 * {@code <game dir>/captures/<label>-<timestamp>/}. Started/stopped from
 * MCP tools; flushed from the client thread on every END_CLIENT_TICK while
 * active so a crash doesn't lose the run.
 *
 * <p>Tick ordering contract (see McpClientMod wiring): {@code InputScriptEngine.tick()}
 * runs on START_CLIENT_TICK (key state applied before this tick's physics),
 * {@code TelemetryBuffer.sample()} and this class's {@link #onTick()} run on
 * END_CLIENT_TICK (after physics + packet dispatch) — so ticks.jsonl's
 * {@code input} for tick N is exactly the input that produced tick N's
 * {@code pos}/{@code vel}, zero-lag, no Python-side shifting needed.
 */
public final class CaptureWriter {
    private final TelemetryBuffer telemetry;

    private Path dir;
    private BufferedWriter ticksOut;
    private BufferedWriter outboundOut;
    private BufferedWriter inboundOut;
    private int lastFlushedTick = 0;

    public CaptureWriter(TelemetryBuffer telemetry) {
        this.telemetry = telemetry;
    }

    public boolean isActive() {
        return dir != null;
    }

    public synchronized String start(String label) throws ToolException {
        if (isActive()) {
            throw new ToolException("Capture already running in " + dir);
        }
        String safeLabel = (label == null || label.isBlank()) ? "capture" : label.replaceAll("[^A-Za-z0-9_-]", "_");
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now());
        File gameDir = Minecraft.getInstance().gameDirectory;
        dir = gameDir.toPath().resolve("captures").resolve(safeLabel + "-" + stamp);
        try {
            Files.createDirectories(dir);
            ticksOut = Files.newBufferedWriter(dir.resolve("ticks.jsonl"), StandardCharsets.UTF_8);
            outboundOut = Files.newBufferedWriter(dir.resolve("outbound.jsonl"), StandardCharsets.UTF_8);
            inboundOut = Files.newBufferedWriter(dir.resolve("inbound.jsonl"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            dir = null;
            throw new ToolException("Failed to start capture: " + e.getMessage(), e);
        }
        lastFlushedTick = telemetry.currentTick();
        NetTaps.clear();
        return dir.toString();
    }

    public synchronized String stop() throws ToolException {
        if (!isActive()) {
            throw new ToolException("No capture running");
        }
        flushNetTaps();
        String result = dir.toString();
        closeQuietly(ticksOut);
        closeQuietly(outboundOut);
        closeQuietly(inboundOut);
        ticksOut = null;
        outboundOut = null;
        inboundOut = null;
        dir = null;
        return result;
    }

    /** Call once per END_CLIENT_TICK while active: appends new tick records + drains packet taps. */
    public synchronized void onTick() {
        if (!isActive()) {
            return;
        }
        List<TickRecord> newRecords = telemetry.range(lastFlushedTick + 1, telemetry.currentTick());
        try {
            for (TickRecord record : newRecords) {
                ticksOut.write(record.toJson().toString());
                ticksOut.newLine();
            }
            if (!newRecords.isEmpty()) {
                lastFlushedTick = newRecords.get(newRecords.size() - 1).tick();
                ticksOut.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        flushNetTaps();
    }

    private void flushNetTaps() {
        try {
            for (var move : NetTaps.drainOutbound()) {
                outboundOut.write(move.toJson().toString());
                outboundOut.newLine();
            }
            outboundOut.flush();
            for (var pos : NetTaps.drainInbound()) {
                inboundOut.write(pos.toJson().toString());
                inboundOut.newLine();
            }
            inboundOut.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void closeQuietly(BufferedWriter writer) {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException ignored) {
        }
    }
}
