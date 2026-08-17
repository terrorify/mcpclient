package dev.terrorify.tools.mcpclient.mcp;

/** Thrown by an {@link McpTool} to report a tool-level failure to the MCP caller. */
public class ToolException extends Exception {
    public ToolException(String message) {
        super(message);
    }

    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
