package com.example.addon.modules;

import com.example.addon.ExampleAddon;
import com.example.addon.mixin.KeyMappingAccessor;
import autismclient.modules.Module;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class SnapTap extends Module {
    private boolean prevLeft = false;
    private boolean prevRight = false;
    private boolean prevForward = false;
    private boolean prevBack = false;

    private boolean lastStrafe = false;
    private boolean lastAxis = false;

    public SnapTap() {
        super(ExampleAddon.ID + ":snaptap", "SnapTap", "Favors the newest movement press when opposing keys are held simultaneously.");
    }

    @Override
    public void onDisable() {
        if (MC.options == null) return;
        MC.options.keyLeft.setDown(pressed(MC.options.keyLeft));
        MC.options.keyRight.setDown(pressed(MC.options.keyRight));
        MC.options.keyUp.setDown(pressed(MC.options.keyUp));
        MC.options.keyDown.setDown(pressed(MC.options.keyDown));
        prevLeft = prevRight = prevForward = prevBack = false;
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.gui.screen() != null) return;

        boolean left = pressed(MC.options.keyLeft);
        boolean right = pressed(MC.options.keyRight);
        boolean forward = pressed(MC.options.keyUp);
        boolean back = pressed(MC.options.keyDown);

        updateAxisStates(left, right, forward, back);
        applyKeyStates(left, right, forward, back);

        prevLeft = left;
        prevRight = right;
        prevForward = forward;
        prevBack = back;
    }

    private void updateAxisStates(boolean left, boolean right, boolean forward, boolean back) {
        if (left && !prevLeft) lastStrafe = true;
        if (right && !prevRight) lastStrafe = false;
        if (forward && !prevForward) lastAxis = true;
        if (back && !prevBack) lastAxis = false;
    }

    private void applyKeyStates(boolean left, boolean right, boolean forward, boolean back) {
        MC.options.keyLeft.setDown((left && right) ? lastStrafe : left);
        MC.options.keyRight.setDown((left && right) ? !lastStrafe : right);
        MC.options.keyUp.setDown((forward && back) ? lastAxis : forward);
        MC.options.keyDown.setDown((forward && back) ? !lastAxis : back);
    }

    private boolean pressed(KeyMapping key) {
        InputConstants.Key boundKey = ((KeyMappingAccessor) key).getBoundKey();
        return GLFW.glfwGetKey(MC.getWindow().handle(), boundKey.getValue()) == GLFW.GLFW_PRESS;
    }
}
