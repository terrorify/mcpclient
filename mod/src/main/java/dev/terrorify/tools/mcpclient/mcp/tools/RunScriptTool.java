package dev.terrorify.tools.mcpclient.mcp.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.terrorify.tools.mcpclient.input.InputScriptEngine;
import dev.terrorify.tools.mcpclient.mcp.McpTool;
import dev.terrorify.tools.mcpclient.mcp.ToolException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Starts a tick-scripted input sequence and returns immediately (does NOT
 * block for the script's duration — the engine advances on
 * START_CLIENT_TICK; poll get_state/get_ticks to observe progress).
 */
public final class RunScriptTool implements McpTool {
    private final InputScriptEngine engine;

    public RunScriptTool(InputScriptEngine engine) {
        this.engine = engine;
    }

    @Override
    public String name() {
        return "run_script";
    }

    @Override
    public String description() {
        return "Start a tick-scripted key/look sequence: steps=[{ticks, keys:[forward|back|left|right|jump|sprint|sneak], yaw?, pitch?}]. Returns immediately; does not block for the script's duration.";
    }

    @Override
    public JsonObject inputSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject props = new JsonObject();
        JsonObject steps = new JsonObject();
        steps.addProperty("type", "array");
        props.add("steps", steps);
        schema.add("properties", props);
        JsonArray required = new JsonArray();
        required.add("steps");
        schema.add("required", required);
        return schema;
    }

    @Override
    public JsonObject call(JsonObject args) throws ToolException {
        if (!args.has("steps") || !args.get("steps").isJsonArray()) {
            throw new ToolException("Missing required arg: steps (array)");
        }
        JsonArray stepsJson = args.getAsJsonArray("steps");
        List<InputScriptEngine.Step> steps = new ArrayList<>();
        for (var element : stepsJson) {
            JsonObject stepObj = element.getAsJsonObject();
            int ticks = stepObj.has("ticks") ? stepObj.get("ticks").getAsInt() : 1;

            Set<InputScriptEngine.Key> keys = EnumSet.noneOf(InputScriptEngine.Key.class);
            if (stepObj.has("keys")) {
                for (var keyElement : stepObj.getAsJsonArray("keys")) {
                    keys.add(parseKey(keyElement.getAsString()));
                }
            }

            Float yaw = stepObj.has("yaw") ? stepObj.get("yaw").getAsFloat() : null;
            Float pitch = stepObj.has("pitch") ? stepObj.get("pitch").getAsFloat() : null;

            steps.add(new InputScriptEngine.Step(ticks, keys, yaw, pitch));
        }

        engine.start(steps);

        JsonObject result = new JsonObject();
        result.addProperty("started", true);
        result.addProperty("step_count", steps.size());
        return result;
    }

    private static InputScriptEngine.Key parseKey(String raw) throws ToolException {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "forward", "fwd" -> InputScriptEngine.Key.FORWARD;
            case "back", "backward" -> InputScriptEngine.Key.BACK;
            case "left" -> InputScriptEngine.Key.LEFT;
            case "right" -> InputScriptEngine.Key.RIGHT;
            case "jump" -> InputScriptEngine.Key.JUMP;
            case "sprint" -> InputScriptEngine.Key.SPRINT;
            case "sneak", "shift" -> InputScriptEngine.Key.SNEAK;
            default -> throw new ToolException("Unknown key: " + raw);
        };
    }
}
