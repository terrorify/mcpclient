package dev.terrorify.tools.mcpclient.spacetime;

/**
 * SpacetimeDB endpoint configuration for the debug-session bridge (see
 * {@link DebugSessionReporter}). Every field is a system property so it can
 * be pointed at whatever deployment the connected server actually uses —
 * mirrors Stomzy's own SPACETIME_URI/SPACETIME_DB/SPACETIME_HOT_DB
 * configurability (SpacetimeBootstrap.java), since there is no single fixed
 * "real" db name across dev/staging/prod.
 *
 * <p>{@code -Dmcpclient.spacetime.enabled=false} disables the bridge entirely
 * (no HTTP calls at all) — useful when connecting to a non-Terrorify server
 * (e.g. this mod's own local vanilla smoke tests) where these tables don't
 * exist.
 */
public final class SpacetimeConfig {
    private SpacetimeConfig() {
    }

    public static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("mcpclient.spacetime.enabled", "false"));
    public static final String URI = System.getProperty("mcpclient.spacetime.uri", "http://127.0.0.1:3000");
    /** cold module — used once per connect to resolve this player's UUID -> spacetime_id. */
    public static final String COLD_DB = System.getProperty("mcpclient.spacetime.coldDb", "ColdStorage");
    /** hot module — hosts mcp_debug_session / mcp_client_tick / movement_authority_debug_report. */
    public static final String HOT_DB = System.getProperty("mcpclient.spacetime.hotDb", "HotStorage");
}
