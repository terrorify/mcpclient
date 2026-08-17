package dev.terrorify.tools.mcpclient.input;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tick-scripted key press/release + look targets, driven from
 * {@code END_CLIENT_TICK}. Actuates the same {@link KeyMapping}s a human
 * would hold — vanilla physics untouched, this only presses virtual keys.
 */
public final class InputScriptEngine {
    public enum Key {
        FORWARD, BACK, LEFT, RIGHT, JUMP, SPRINT, SNEAK
    }

    public record Step(int ticks, Set<Key> keys, Float yaw, Float pitch) {
    }

    private final Deque<Step> queue = new ArrayDeque<>();
    private Step activeStep;
    private int remainingTicks;
    private volatile boolean running;

    public synchronized void start(List<Step> steps) {
        stop();
        queue.addAll(steps);
        advance();
    }

    public synchronized void stop() {
        queue.clear();
        activeStep = null;
        remainingTicks = 0;
        running = false;
        releaseAll();
    }

    public boolean isRunning() {
        return running;
    }

    /** Call once per END_CLIENT_TICK. */
    public synchronized void tick() {
        if (activeStep == null) {
            return;
        }
        remainingTicks--;
        if (remainingTicks <= 0) {
            advance();
        }
    }

    private void advance() {
        releaseAll();
        activeStep = queue.poll();
        if (activeStep == null) {
            running = false;
            return;
        }
        running = true;
        remainingTicks = Math.max(1, activeStep.ticks());
        applyKeys(activeStep.keys());
        applyLook(activeStep.yaw(), activeStep.pitch());
    }

    private void applyKeys(Set<Key> keys) {
        Map<Key, KeyMapping> mapping = keyMappings();
        for (Key key : keys) {
            KeyMapping km = mapping.get(key);
            if (km != null) {
                km.setDown(true);
            }
        }
    }

    private void releaseAll() {
        for (KeyMapping km : keyMappings().values()) {
            km.setDown(false);
        }
    }

    private void applyLook(Float yaw, Float pitch) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (yaw != null) {
            player.setYRot(yaw);
        }
        if (pitch != null) {
            player.setXRot(pitch);
        }
    }

    private Map<Key, KeyMapping> keyMappings() {
        Options options = Minecraft.getInstance().options;
        Map<Key, KeyMapping> map = new EnumMap<>(Key.class);
        if (options == null) {
            return map;
        }
        map.put(Key.FORWARD, options.keyUp);
        map.put(Key.BACK, options.keyDown);
        map.put(Key.LEFT, options.keyLeft);
        map.put(Key.RIGHT, options.keyRight);
        map.put(Key.JUMP, options.keyJump);
        map.put(Key.SPRINT, options.keySprint);
        map.put(Key.SNEAK, options.keyShift);
        return map;
    }
}
