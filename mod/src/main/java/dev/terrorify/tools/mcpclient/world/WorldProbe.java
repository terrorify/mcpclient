package dev.terrorify.tools.mcpclient.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.terrorify.tools.mcpclient.mcp.ToolException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Reads the client's own chunk cache — what the client actually has, not server truth. */
public final class WorldProbe {
    public static final int MAX_VOLUME = 16 * 16 * 16;

    private WorldProbe() {
    }

    public static JsonObject getBlock(int x, int y, int z) throws ToolException {
        ClientLevel level = requireLevel();
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        JsonObject o = new JsonObject();
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        o.addProperty("block", state.toString());
        return o;
    }

    public static JsonObject getArea(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) throws ToolException {
        ClientLevel level = requireLevel();
        int lx = Math.min(minX, maxX), hx = Math.max(minX, maxX);
        int ly = Math.min(minY, maxY), hy = Math.max(minY, maxY);
        int lz = Math.min(minZ, maxZ), hz = Math.max(minZ, maxZ);

        long volume = (long) (hx - lx + 1) * (hy - ly + 1) * (hz - lz + 1);
        if (volume > MAX_VOLUME) {
            throw new ToolException("Area too large: " + volume + " blocks (max " + MAX_VOLUME + ")");
        }

        JsonArray blocks = new JsonArray();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = lx; x <= hx; x++) {
            for (int y = ly; y <= hy; y++) {
                for (int z = lz; z <= hz; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    JsonObject entry = new JsonObject();
                    entry.addProperty("x", x);
                    entry.addProperty("y", y);
                    entry.addProperty("z", z);
                    entry.addProperty("block", state.toString());
                    blocks.add(entry);
                }
            }
        }

        JsonObject result = new JsonObject();
        result.add("blocks", blocks);
        return result;
    }

    private static ClientLevel requireLevel() throws ToolException {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            throw new ToolException("Not connected: no client level loaded");
        }
        return level;
    }
}
