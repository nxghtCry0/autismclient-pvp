package com.example.addon.modules;

import com.example.addon.ExampleAddon;
import autismclient.modules.Module;
import autismclient.api.module.IntSetting;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismRotationUtil;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class PearlCatch extends Module {
    private final IntSetting delay = add(new IntSetting("delay", "Delay (Ticks)", 5, 0, 20, 1));

    private boolean active = false;
    private int ticksElapsed = 0;
    private int originalSlot = -1;
    private int windChargeSlot = -1;
    private float targetYaw, targetPitch;

    public PearlCatch() {
        super(ExampleAddon.ID + ":pearlcatch", "PearlCatch", "Quickly throws an ender pearl and redirects a wind charge to catch up.");
    }

    @Override
    public void onEnable() {
        if (MC.player == null || MC.getConnection() == null || MC.gameMode == null) {
            setEnabled(false);
            return;
        }

        int pearlSlot = findItem("ender_pearl");
        windChargeSlot = findItem("wind_charge");

        if (pearlSlot == -1 || windChargeSlot == -1) {
            setEnabled(false);
            return;
        }

        originalSlot = MC.player.getInventory().getSelectedSlot();
        targetYaw = MC.player.getYRot();
        targetPitch = MC.player.getXRot();
        
        AutismInventoryHelper.selectHotbarSlot(MC, pearlSlot);
        active = true;
        ticksElapsed = 0;
    }

    @Override
    public void tick() {
        if (!active || MC.player == null || MC.getConnection() == null) {
            setEnabled(false);
            return;
        }

        // Apply rotations using Autism's rotation helper to sync client/server angles
        AutismRotationUtil.apply(MC.player, new AutismRotationUtil.Rotation(targetYaw, targetPitch), true);

        ticksElapsed++;
        
        if (ticksElapsed == 1) {
            MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
            MC.player.swing(InteractionHand.MAIN_HAND);
        }

        int del = delay.get();

        if (ticksElapsed == del + 1) {
            AutismInventoryHelper.selectHotbarSlot(MC, windChargeSlot);
        }
        
        if (ticksElapsed >= del + 2) {
            MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
            MC.player.swing(InteractionHand.MAIN_HAND);

            AutismInventoryHelper.selectHotbarSlot(MC, originalSlot);
            active = false;
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        active = false;
    }

    private int findItem(String targetName) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = MC.player.getInventory().getItem(i);
            String itemName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            if (itemName.equals(targetName)) {
                return i;
            }
        }
        return -1;
    }
}
