package dev.terrorify.tools.mcpclient.net;

import com.google.gson.JsonObject;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe sinks for packet-level taps. Written from the Netty event-loop
 * thread inside {@code ConnectionSendMixin}/{@code ConnectionReceiveMixin};
 * consumed from the client thread by {@code CaptureWriter}/MCP tools.
 * {@code snapshot*} copies without consuming (for get_net_stats-style peeks);
 * {@code drain*} consumes so repeated capture flushes never duplicate.
 */
public final class NetTaps {
    private NetTaps() {
    }

    /** Stamped by {@code TelemetryBuffer.sample()} each END_CLIENT_TICK; read (not written) from Netty threads. */
    public static volatile int currentTick = 0;

    private static final int MAX_QUEUED = 24_000;
    public static final AtomicInteger LAST_TELEPORT_ID = new AtomicInteger(-1);


    public record OutboundMove(
        int tick, long wallNs, String packetType,
        boolean hasPos, double x, double y, double z,
        boolean hasRot, float yaw, float pitch,
        boolean onGround, boolean horizontalCollision
    ) {
        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("tick", tick);
            o.addProperty("wall_ns", wallNs);
            o.addProperty("packet_type", packetType);
            o.addProperty("has_pos", hasPos);
            o.addProperty("x", x);
            o.addProperty("y", y);
            o.addProperty("z", z);
            o.addProperty("has_rot", hasRot);
            o.addProperty("yaw", yaw);
            o.addProperty("pitch", pitch);
            o.addProperty("on_ground", onGround);
            o.addProperty("horizontal_collision", horizontalCollision);
            return o;
        }
    }

    public record InboundPosition(
        int tick, long wallNs, int id,
        double x, double y, double z,
        double dx, double dy, double dz,
        float yaw, float pitch
    ) {
        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("tick", tick);
            o.addProperty("wall_ns", wallNs);
            o.addProperty("id", id);
            o.addProperty("x", x);
            o.addProperty("y", y);
            o.addProperty("z", z);
            o.addProperty("dx", dx);
            o.addProperty("dy", dy);
            o.addProperty("dz", dz);
            o.addProperty("yaw", yaw);
            o.addProperty("pitch", pitch);
            return o;
        }
    }

    private static final Queue<OutboundMove> OUTBOUND = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger OUTBOUND_SIZE = new AtomicInteger();
    private static final Queue<InboundPosition> INBOUND = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger INBOUND_SIZE = new AtomicInteger();

    public static void recordOutbound(ServerboundMovePlayerPacket packet) {
        OutboundMove move = new OutboundMove(
            currentTick,
            System.nanoTime(),
            packet.getClass().getSimpleName(),
            packet.hasPosition(), packet.getX(Double.NaN), packet.getY(Double.NaN), packet.getZ(Double.NaN),
            packet.hasRotation(), packet.getYRot(Float.NaN), packet.getXRot(Float.NaN),
            packet.isOnGround(), packet.horizontalCollision()
        );
        OUTBOUND.add(move);
        if (OUTBOUND_SIZE.incrementAndGet() > MAX_QUEUED) {
            OUTBOUND.poll();
            OUTBOUND_SIZE.decrementAndGet();
        }
    }

    public static void recordInbound(ClientboundPlayerPositionPacket packet) {
        LAST_TELEPORT_ID.set(packet.id());
        PositionMoveRotation change = packet.change();
        Vec3 pos = change.position();
        Vec3 delta = change.deltaMovement();
        InboundPosition in = new InboundPosition(
            currentTick,
            System.nanoTime(),
            packet.id(),
            pos.x, pos.y, pos.z,
            delta.x, delta.y, delta.z,
            change.yRot(), change.xRot()
        );
        INBOUND.add(in);
        if (INBOUND_SIZE.incrementAndGet() > MAX_QUEUED) {
            INBOUND.poll();
            INBOUND_SIZE.decrementAndGet();
        }
    }

    /** Consumes and returns every queued outbound record. */
    public static List<OutboundMove> drainOutbound() {
        List<OutboundMove> out = new ArrayList<>();
        OutboundMove m;
        while ((m = OUTBOUND.poll()) != null) {
            OUTBOUND_SIZE.decrementAndGet();
            out.add(m);
        }
        return out;
    }

    /** Consumes and returns every queued inbound record. */
    public static List<InboundPosition> drainInbound() {
        List<InboundPosition> out = new ArrayList<>();
        InboundPosition m;
        while ((m = INBOUND.poll()) != null) {
            INBOUND_SIZE.decrementAndGet();
            out.add(m);
        }
        return out;
    }

    public static void clear() {
        drainOutbound();
        drainInbound();
    }
}
