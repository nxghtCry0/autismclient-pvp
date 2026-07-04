package com.example.addon.modules;

import com.example.addon.ExampleAddon;
import com.example.addon.mixin.InventoryAccessor;
import com.example.addon.mixin.MinecraftAccessor;
import autismclient.modules.Module;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.DoubleSetting;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class MaceSwap extends Module {
    public static MaceSwap INSTANCE;

    private final BoolSetting notOnAxe = add(new BoolSetting("notOnAxe", "Not on Axe", false));
    private final BoolSetting weaponOnly = add(new BoolSetting("weaponOnly", "Weapon Only", true));
    private final DoubleSetting densityHeight = add(new DoubleSetting("densityHeight", "Min Fall Height", 1.5, 0.0, 10.0, 0.5));

    private boolean isSwapping = false;
    private int originalSlot = -1;
    private int swapTicks = 0;
    private double highestY = 0.0;

    public MaceSwap() {
        super(ExampleAddon.ID + ":maceswap", "MaceSwap", "Automatically swaps to Mace when falling and attacks for massive critical damage.");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (MC.player != null) {
            highestY = MC.player.getY();
        }
        resetState();
    }

    @Override
    public void onDisable() {
        if (isSwapping && MC.player != null && originalSlot != -1) {
            ((InventoryAccessor) MC.player.getInventory()).pvpaddonSetSelected(originalSlot);
        }
        resetState();
    }

    @Override
    public void tick() {
        if (MC.player == null) return;

        boolean isOnGroundNow = MC.player.onGround();
        if (isOnGroundNow) {
            highestY = MC.player.getY();
        } else {
            highestY = Math.max(highestY, MC.player.getY());
        }

        if (!isSwapping) return;

        swapTicks++;
        if (swapTicks == 1) {
            if (MC.gameMode != null && MC.crosshairPickEntity != null) {
                ((MinecraftAccessor) MC).pvpaddonStartAttack();
            } else {
                ((InventoryAccessor) MC.player.getInventory()).pvpaddonSetSelected(originalSlot);
                resetState();
            }
        } else if (swapTicks >= 3) {
            ((InventoryAccessor) MC.player.getInventory()).pvpaddonSetSelected(originalSlot);
            resetState();
        }
    }

    public boolean handleAttack() {
        if (MC.player == null || MC.level == null || isSwapping) {
            return false;
        }

        ItemStack mainHandStack = MC.player.getMainHandItem();
        if (Boolean.TRUE.equals(weaponOnly.get()) && !isWeapon(mainHandStack)) {
            return false;
        }

        if (Boolean.TRUE.equals(notOnAxe.get())) {
            String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mainHandStack.getItem()).getPath().toLowerCase();
            if (name.contains("axe")) {
                return false;
            }
        }

        HitResult hitResult = MC.hitResult;
        if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) {
            return false;
        }

        EntityHitResult entityHit = (EntityHitResult) hitResult;
        if (!(entityHit.getEntity() instanceof LivingEntity target) || !target.isAlive() || target == MC.player) {
            return false;
        }

        if (mainHandStack.getItem() == Items.MACE) {
            return false;
        }

        double fallDist = Math.max(0.0, highestY - MC.player.getY());
        if (fallDist < densityHeight.get()) {
            return false;
        }

        int maceSlot = findMaceInHotbar();
        if (maceSlot == -1) {
            return false;
        }

        originalSlot = ((InventoryAccessor) MC.player.getInventory()).pvpaddonGetSelected();
        ((InventoryAccessor) MC.player.getInventory()).pvpaddonSetSelected(maceSlot);
        isSwapping = true;
        swapTicks = 0;
        return true;
    }

    private boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
        return name.contains("sword") || name.contains("axe") || name.contains("mace") || name.contains("trident");
    }

    private int findMaceInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = MC.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == Items.MACE) {
                return i;
            }
        }
        return -1;
    }

    private void resetState() {
        isSwapping = false;
        originalSlot = -1;
        swapTicks = 0;
    }
}
