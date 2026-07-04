package com.example.addon.modules;

import com.example.addon.ExampleAddon;
import autismclient.modules.Module;
import autismclient.api.module.BoolSetting;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class AutoCrit extends Module {
    private final BoolSetting onlyOnAttack = add(new BoolSetting("onlyOnAttack", "Only On Attack Key", true));
    private final BoolSetting weaponOnly = add(new BoolSetting("weaponOnly", "Weapon Only", true));

    private boolean simulatedJump = false;

    public AutoCrit() {
        super(ExampleAddon.ID + ":autocrit", "AutoCrit", "Automatically jumps before an attack to inflict critical hits.");
    }

    @Override
    public void onDisable() {
        releaseJump();
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.level == null || MC.options == null) return;

        if (simulatedJump && !MC.player.onGround()) {
            releaseJump();
        }

        if (!MC.player.onGround() || MC.player.onClimbable() || MC.player.isInWater() || MC.options.keyJump.isDown()) {
            return;
        }

        if (Boolean.TRUE.equals(onlyOnAttack.get()) && !MC.options.keyAttack.isDown()) {
            return;
        }

        if (Boolean.TRUE.equals(weaponOnly.get()) && !isHoldingWeapon()) {
            return;
        }

        if (MC.player.getAttackStrengthScale(0.5F) >= 0.9F) {
            HitResult hitResult = MC.hitResult;
            if (hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target && target.isAlive() && target.deathTime == 0) {
                doJump();
            }
        }
    }

    private void doJump() {
        if (MC.options != null && !MC.options.keyJump.isDown()) {
            MC.options.keyJump.setDown(true);
            simulatedJump = true;
        }
    }

    private void releaseJump() {
        if (simulatedJump && MC.options != null) {
            MC.options.keyJump.setDown(false);
            simulatedJump = false;
        }
    }

    private boolean isHoldingWeapon() {
        if (MC.player == null) return false;
        net.minecraft.world.item.Item item = MC.player.getMainHandItem().getItem();
        String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath().toLowerCase();
        return name.contains("sword") || name.contains("axe") || name.contains("mace") || name.contains("trident");
    }
}
