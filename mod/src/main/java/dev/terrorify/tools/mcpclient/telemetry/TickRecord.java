package dev.terrorify.tools.mcpclient.telemetry;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Immutable per-tick snapshot of client-observed player truth. See PLAN.md TickRecord schema. */
public record TickRecord(
    int tick,
    long wallNs,
    double x, double y, double z,
    double vx, double vy, double vz,
    boolean onGround,
    float yaw, float pitch,
    String pose,
    boolean sprinting,
    double bbMinX, double bbMinY, double bbMinZ,
    double bbMaxX, double bbMaxY, double bbMaxZ,
    String blockBelow,
    String blockFeet,
    boolean inputForward, boolean inputBackward, boolean inputLeft, boolean inputRight,
    boolean inputJump, boolean inputSprint, boolean inputShift,
    int lastTeleportId
) {
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("tick", tick);
        o.addProperty("wall_ns", wallNs);

        JsonArray pos = new JsonArray();
        pos.add(x); pos.add(y); pos.add(z);
        o.add("pos", pos);

        JsonArray vel = new JsonArray();
        vel.add(vx); vel.add(vy); vel.add(vz);
        o.add("vel", vel);

        o.addProperty("on_ground", onGround);
        o.addProperty("yaw", yaw);
        o.addProperty("pitch", pitch);
        o.addProperty("pose", pose);
        o.addProperty("sprinting", sprinting);

        JsonArray bb = new JsonArray();
        bb.add(bbMinX); bb.add(bbMinY); bb.add(bbMinZ);
        bb.add(bbMaxX); bb.add(bbMaxY); bb.add(bbMaxZ);
        o.add("bb", bb);

        o.addProperty("block_below", blockBelow);
        o.addProperty("block_feet", blockFeet);

        JsonObject input = new JsonObject();
        input.addProperty("fwd", inputForward);
        input.addProperty("back", inputBackward);
        input.addProperty("left", inputLeft);
        input.addProperty("right", inputRight);
        input.addProperty("jump", inputJump);
        input.addProperty("sprint", inputSprint);
        input.addProperty("shift", inputShift);
        o.add("input", input);

        o.addProperty("last_teleport_id", lastTeleportId);
        return o;
    }
}
