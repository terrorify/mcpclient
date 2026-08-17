package dev.terrorify.tools.mcpclient.spacetime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.terrorify.tools.mcpclient.telemetry.TelemetryBuffer;
import dev.terrorify.tools.mcpclient.telemetry.TickRecord;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Announces mcpclient's presence to the connected server's SpacetimeDB
 * backend and streams ground-truth ticks for its own live comparison —
 * see spacetime/hot/src/{tables,reducers}/mcp_debug.rs. Stomzy needs no
 * code of its own: this writes directly to the hot module's reducers over
 * HTTP, and the comparison hooks into the {@code player_physics_state_update}
 * reducer Stomzy already calls ~20 Hz/player.
 *
 * <p>No-ops entirely (zero HTTP calls) unless {@link SpacetimeConfig#ENABLED}.
 * Identity resolution (UUID -> spacetime_id) happens once per connect via a
 * SQL lookup against {@code player_identity} on the cold module; reporting
 * only starts once that resolves, so connecting to a non-Terrorify server
 * (no such table) just silently never reports anything.
 */
public final class DebugSessionReporter {
    private static final Logger LOGGER = LoggerFactory.getLogger("mcpclient");
    private static final String MOD_VERSION = "1.0.0";
    /** Report every N ticks — ~2/sec at 20 TPS. Frequent enough to catch drift, cheap enough for HTTP. */
    private static final int REPORT_INTERVAL_TICKS = 10;

    private final SpacetimeClient client = new SpacetimeClient();
    private final AtomicLong spacetimeId = new AtomicLong(0);
    private volatile boolean sessionActive = false;

    /** Call once on {@code ClientPlayConnectionEvents.JOIN}. */
    public void onJoin() {
        if (!SpacetimeConfig.ENABLED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null) {
            return;
        }
        UUID uuid;
        try {
            uuid = mc.getUser().getProfileId();
        } catch (Exception e) {
            LOGGER.debug("mcpclient: no profile UUID available, skipping spacetime debug session", e);
            return;
        }
        if (uuid == null) {
            return;
        }

        String sql = "SELECT spacetime_id FROM player_identity WHERE uuid = " + toU128(uuid);
        client.sqlRowsAsync(SpacetimeConfig.COLD_DB, sql).thenAccept(rows -> {
            if (rows.isEmpty()) {
                LOGGER.debug("mcpclient: no player_identity row for this UUID on {} — spacetime debug bridge inactive",
                    SpacetimeConfig.COLD_DB);
                return;
            }
            long id = extractSpacetimeId(rows.get(0).getAsJsonObject());
            if (id == 0) {
                return;
            }
            spacetimeId.set(id);
            sessionActive = true;
            client.callReducerAsync(SpacetimeConfig.HOT_DB, "set_mcp_debug_session", args(id, true, MOD_VERSION));
            LOGGER.info("mcpclient: spacetime debug session active (spacetime_id={})", id);
        });
    }

    /** Call once on {@code ClientPlayConnectionEvents.DISCONNECT}. */
    public void onDisconnect() {
        if (!sessionActive) {
            return;
        }
        sessionActive = false;
        client.callReducerAsync(SpacetimeConfig.HOT_DB, "set_mcp_debug_session", args(spacetimeId.get(), false, MOD_VERSION));
        spacetimeId.set(0);
    }

    /** Call once per END_CLIENT_TICK; internally throttles to {@link #REPORT_INTERVAL_TICKS}. */
    public void onTick(TelemetryBuffer telemetry) {
        if (!sessionActive) {
            return;
        }
        int tick = telemetry.currentTick();
        if (tick % REPORT_INTERVAL_TICKS != 0) {
            return;
        }
        TickRecord record = telemetry.latest();
        if (record == null) {
            return;
        }

        JsonArray args = new JsonArray();
        args.add(spacetimeId.get());
        args.add(record.tick());
        args.add(record.x()); args.add(record.y()); args.add(record.z());
        args.add(record.vx()); args.add(record.vy()); args.add(record.vz());
        args.add(record.onGround());
        args.add(record.yaw()); args.add(record.pitch());
        args.add(record.inputForward()); args.add(record.inputBackward());
        args.add(record.inputLeft()); args.add(record.inputRight());
        args.add(record.inputJump()); args.add(record.inputSprint()); args.add(record.inputShift());

        client.callReducerAsync(SpacetimeConfig.HOT_DB, "report_mcp_client_tick", args);
    }

    private static JsonArray args(long spacetimeId, boolean enabled, String modVersion) {
        JsonArray args = new JsonArray();
        args.add(spacetimeId);
        args.add(enabled);
        args.add(modVersion);
        return args;
    }

    /** Mojang UUIDs are RFC 4122 128-bit big-endian — same layout as Rust's u128, msb<<64|lsb. */
    private static BigInteger toU128(UUID uuid) {
        BigInteger msb = BigInteger.valueOf(uuid.getMostSignificantBits()).and(new BigInteger("FFFFFFFFFFFFFFFF", 16));
        BigInteger lsb = BigInteger.valueOf(uuid.getLeastSignificantBits()).and(new BigInteger("FFFFFFFFFFFFFFFF", 16));
        return msb.shiftLeft(64).or(lsb);
    }

    /** SpacetimeDB SQL rows come back as an array whose first element is the column value. */
    private static long extractSpacetimeId(JsonObject row) {
        try {
            var entry = row.entrySet().iterator();
            if (entry.hasNext()) {
                return entry.next().getValue().getAsLong();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}
