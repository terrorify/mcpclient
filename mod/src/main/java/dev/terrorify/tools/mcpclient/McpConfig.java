package dev.terrorify.tools.mcpclient;

/** Reads {@code -Dmcpclient.port=NNNN} (default 47800) so multiple client instances can run side by side. */
public final class McpConfig {
    private McpConfig() {
    }

    public static final String HOST = System.getProperty("mcpclient.host", "127.0.0.1");
    public static final int PORT = Integer.getInteger("mcpclient.port", 47800);
}
