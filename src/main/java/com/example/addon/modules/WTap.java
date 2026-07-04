package com.example.addon.modules;

import com.example.addon.ExampleAddon;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.util.AutismKeyMappingBridge;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

public final class WTap extends Module {
    private static WTap INSTANCE;

    private final ChoiceSetting mode = add(new ChoiceSetting("mode", "WTap Mode", "Auto", "Auto", "Normal", "Silent"));
    private final DoubleSetting chance = add(new DoubleSetting("chance", "Chance (%)", 100.0, 0.0, 100.0, 1.0));
    private final BoolSetting onlyPlayers = add(new BoolSetting("onlyPlayers", "Only Players", false));
    private final DoubleSetting waitTicks = add(new DoubleSetting("waitTicks", "Wait Ticks", 0.0, 0.0, 5.0, 1.0));
    private final DoubleSetting actionTicks = add(new DoubleSetting("actionTicks", "Action Ticks", 1.0, 1.0, 5.0, 1.0));
    private final DoubleSetting jitterTicks = add(new DoubleSetting("jitterTicks", "Jitter Ticks", 1.0, 0.0, 3.0, 1.0));

    private int phase = 0;
    private int ticksRemaining = 0;

    public WTap() {
        super(ExampleAddon.ID + ":wtap", "WTap", "Releases forward key on hit to reset sprint knockback.");
        INSTANCE = this;
    }

    private boolean isSilent() {
        String m = mode.get();
        if (m.equalsIgnoreCase("Silent")) return true;
        if (m.equalsIgnoreCase("Normal")) return false;
        // fallback to silent by default if Auto
        return true;
    }

    public static void onAttackLanded(Entity target) {
        if (INSTANCE == null || !INSTANCE.isEnabled() || MC.player == null || MC.options == null) return;
        if (INSTANCE.phase != 0) return;
        if (!MC.options.keyUp.isDown()) return;

        if (Math.random() * 100.0 > INSTANCE.chance.get()) return;

        if (INSTANCE.onlyPlayers.get() && !(target instanceof Player)) return;

        INSTANCE.phase = 1;
        int base = (int) (double) INSTANCE.waitTicks.get();
        int jitter = (int) (double) INSTANCE.jitterTicks.get();
        INSTANCE.ticksRemaining = base + (int) (Math.random() * (jitter + 1));
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.options == null || phase == 0) return;

        switch (phase) {
            case 1 -> {
                if (!MC.options.keyUp.isDown()) { phase = 0; return; }
                ticksRemaining--;
                if (ticksRemaining <= 0) {
                    if (isSilent()) {
                        MC.player.setSprinting(false);
                    } else {
                        MC.options.keyUp.setDown(false);
                    }
                    phase = 2;
                    int base = (int) (double) actionTicks.get();
                    int jitter = (int) (double) jitterTicks.get();
                    ticksRemaining = base + (int) (Math.random() * (jitter + 1));
                }
            }
            case 2 -> {
                ticksRemaining--;
                if (ticksRemaining <= 0) {
                    if (isSilent()) {
                        MC.player.setSprinting(true);
                    } else if (isPhysicallyHoldingW()) {
                        MC.options.keyUp.setDown(true);
                    }
                    phase = 0;
                }
            }
        }
    }

    public static boolean shouldSilentStopSprint() {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return false;
        if (INSTANCE.phase != 2) return false;
        return INSTANCE.isSilent();
    }

    private boolean isPhysicallyHoldingW() {
        long window = getWindowHandle();
        if (window == 0) return false;
        return GLFW.glfwGetKey(window, getKeyCode(MC.options.keyUp)) == GLFW.GLFW_PRESS;
    }

    private int getKeyCode(net.minecraft.client.KeyMapping mapping) {
        try {
            for (java.lang.reflect.Method m : mapping.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getName().contains("InputConstants$Key")) {
                    Object keyObj = m.invoke(mapping);
                    java.lang.reflect.Method getValue = keyObj.getClass().getMethod("getValue");
                    return (int) getValue.invoke(keyObj);
                }
            }
        } catch (Exception ignored) {}
        return mapping.getDefaultKey().getValue();
    }

    private long getWindowHandle() {
        try {
            for (java.lang.reflect.Field f : MC.getWindow().getClass().getDeclaredFields()) {
                if (f.getType() == long.class) {
                    f.setAccessible(true);
                    return f.getLong(MC.getWindow());
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    @Override
    public void onDisable() {
        if (phase == 2 && MC.options != null) {
            if (isSilent()) {
                if (MC.player != null) {
                    MC.player.setSprinting(true);
                }
            } else if (isPhysicallyHoldingW()) {
                MC.options.keyUp.setDown(true);
            }
        }
        phase = 0;
        ticksRemaining = 0;
    }
}
