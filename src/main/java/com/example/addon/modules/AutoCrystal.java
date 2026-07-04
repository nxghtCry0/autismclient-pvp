package com.example.addon.modules;

import com.example.addon.ExampleAddon;
import autismclient.modules.Module;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismInputClicker;
import autismclient.util.AutismKeyMappingBridge;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;

public final class AutoCrystal extends Module {
    private final DoubleSetting delay = add(new DoubleSetting("delay", "Delay (ms)", 100.0, 0.0, 500.0, 1.0));
    private final BoolSetting onlyOnRightClick = add(new BoolSetting("onlyOnRightClick", "Only On Right Click", true));
    private final BoolSetting simClick = add(new BoolSetting("simClick", "SimClick", true));
    private final BoolSetting swapToCrystal = add(new BoolSetting("swapToCrystal", "Swap to crystal on Right Click", true));

    private long lastActionTime = 0L;

    public AutoCrystal() {
        super(ExampleAddon.ID + ":autocrystal", "AutoCrystal", "Places and breaks crystals legitimately.");
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.level == null || MC.gameMode == null || MC.gui.screen() != null) {
            return;
        }

        if ((!onlyOnRightClick.get() || MC.options.keyUse.isDown()) &&
            (System.currentTimeMillis() - lastActionTime) >= delay.get()) {

            EndCrystal crystalToBreak = findCrystalToBreak();
            if (crystalToBreak != null) {
                performBreakAction();
            } else {
                HitResult target = MC.hitResult;
                if (swapToCrystal.get() && MC.options.keyUse.isDown() && target instanceof BlockHitResult) {
                    BlockHitResult hit = (BlockHitResult) target;
                    if (MC.level.getBlockState(hit.getBlockPos()).is(Blocks.OBSIDIAN) &&
                        MC.player.getItemInHand(InteractionHand.MAIN_HAND).getItem() != Items.END_CRYSTAL &&
                        MC.player.getItemInHand(InteractionHand.OFF_HAND).getItem() != Items.END_CRYSTAL) {
                        int crystalSlot = findCrystalInHotbar();
                        if (crystalSlot != -1) {
                            AutismInventoryHelper.selectHotbarSlot(MC, crystalSlot);
                        }
                    }
                }

                boolean holdingCrystal = MC.player.getItemInHand(InteractionHand.MAIN_HAND).getItem() == Items.END_CRYSTAL ||
                                        MC.player.getItemInHand(InteractionHand.OFF_HAND).getItem() == Items.END_CRYSTAL;

                if (holdingCrystal && target instanceof BlockHitResult) {
                    BlockHitResult hit = (BlockHitResult) target;
                    Vec3 blockCenter = new Vec3(hit.getBlockPos().getX() + 0.5, hit.getBlockPos().getY() + 0.5, hit.getBlockPos().getZ() + 0.5);
                    if (MC.player.distanceToSqr(blockCenter) <= 20.25 &&
                        isValidCrystalBase(MC.level.getBlockState(hit.getBlockPos()).getBlock()) &&
                        MC.level.isEmptyBlock(hit.getBlockPos().above())) {
                        performPlaceAction();
                    }
                }
            }
        }
    }

    private int findCrystalInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = MC.player.getInventory().getItem(i);
            if (stack.getItem() == Items.END_CRYSTAL) {
                return i;
            }
        }
        return -1;
    }

    private void performBreakAction() {
        if (simClick.get()) {
            AutismKeyMappingBridge.of(MC.options.keyAttack).autism$simulatePress(true);
            AutismKeyMappingBridge.of(MC.options.keyAttack).autism$simulatePress(false);
        } else {
            AutismInputClicker.queueAttackClick();
        }
        lastActionTime = System.currentTimeMillis();
    }

    private void performPlaceAction() {
        if (simClick.get()) {
            AutismKeyMappingBridge.of(MC.options.keyUse).autism$simulatePress(true);
            AutismKeyMappingBridge.of(MC.options.keyUse).autism$simulatePress(false);
        } else {
            AutismInputClicker.queueUseClick();
        }
        lastActionTime = System.currentTimeMillis();
    }

    private EndCrystal findCrystalToBreak() {
        if (simClick.get()) {
            HitResult target = MC.hitResult;
            if (target instanceof EntityHitResult) {
                EntityHitResult ehr = (EntityHitResult) target;
                Entity entity = ehr.getEntity();
                if (entity instanceof EndCrystal) {
                    EndCrystal crystal = (EndCrystal) entity;
                    if (MC.player.distanceToSqr(crystal) <= 20.25) {
                        return crystal;
                    }
                }
            }
            return null;
        }

        for (Entity entity : MC.level.entitiesForRendering()) {
            if (entity instanceof EndCrystal) {
                EndCrystal crystal = (EndCrystal) entity;
                if (MC.player.hasLineOfSight(entity) && MC.player.distanceToSqr(entity) < 20.25) {
                    return crystal;
                }
            }
        }
        return null;
    }

    private boolean isValidCrystalBase(Block block) {
        return (block == Blocks.OBSIDIAN || block == Blocks.BEDROCK);
    }
}
