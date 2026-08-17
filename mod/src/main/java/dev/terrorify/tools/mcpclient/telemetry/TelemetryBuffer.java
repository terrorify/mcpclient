package dev.terrorify.tools.mcpclient.telemetry;

import dev.terrorify.tools.mcpclient.net.NetTaps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Ring buffer of {@link TickRecord}s sampled on {@code END_CLIENT_TICK}.
 * Runs entirely on the client thread — no synchronization needed for reads
 * from MCP tools, since those are also marshaled onto the client thread by
 * {@code ToolRegistry}.
 */
public final class TelemetryBuffer {
    public static final int CAPACITY = 24_000;

    private final ArrayDeque<TickRecord> buffer = new ArrayDeque<>(CAPACITY);
    private int tickCounter = 0;

    /** Call once per END_CLIENT_TICK. Also stamps {@link NetTaps#currentTick} for packet taps. */
    public void sample() {
        tickCounter++;
        NetTaps.currentTick = tickCounter;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        Vec3 vel = player.getDeltaMovement();
        AABB bb = player.getBoundingBox();
        Input keys = player.input != null ? player.input.keyPresses : Input.EMPTY;

        BlockPos feetPos = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        BlockPos belowPos = feetPos.below();
        String blockFeet = "unknown";
        String blockBelow = "unknown";
        if (mc.level != null) {
            blockFeet = mc.level.getBlockState(feetPos).toString();
            blockBelow = mc.level.getBlockState(belowPos).toString();
        }

        TickRecord record = new TickRecord(
            tickCounter,
            System.nanoTime(),
            player.getX(), player.getY(), player.getZ(),
            vel.x, vel.y, vel.z,
            player.onGround(),
            player.getYRot(), player.getXRot(),
            player.getPose().toString(),
            player.isSprinting(),
            bb.minX, bb.minY, bb.minZ,
            bb.maxX, bb.maxY, bb.maxZ,
            blockBelow,
            blockFeet,
            keys.forward(), keys.backward(), keys.left(), keys.right(),
            keys.jump(), keys.sprint(), keys.shift(),
            NetTaps.LAST_TELEPORT_ID.get()
        );

        if (buffer.size() >= CAPACITY) {
            buffer.removeFirst();
        }
        buffer.addLast(record);
    }

    public int currentTick() {
        return tickCounter;
    }

    public void clear() {
        buffer.clear();
        tickCounter = 0;
        NetTaps.currentTick = 0;
    }

    public List<TickRecord> range(int fromTick, int toTick) {
        List<TickRecord> result = new ArrayList<>();
        for (TickRecord r : buffer) {
            if (r.tick() >= fromTick && r.tick() <= toTick) {
                result.add(r);
            }
        }
        return result;
    }

    public List<TickRecord> all() {
        return new ArrayList<>(buffer);
    }

    public TickRecord latest() {
        return buffer.peekLast();
    }
}
