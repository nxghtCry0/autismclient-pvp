package com.example.addon.modules;

import com.example.addon.ExampleAddon;
import autismclient.modules.Module;
import autismclient.api.module.DoubleSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class BetterBoatfly extends Module {
    private final DoubleSetting speed = add(new DoubleSetting("speed", "Speed", 1.0, 0.1, 5.0, 0.1));
    private final DoubleSetting upSpeed = add(new DoubleSetting("upSpeed", "Up Speed", 0.5, 0.1, 2.0, 0.1));

    public BetterBoatfly() {
        super(ExampleAddon.ID + ":betterboatfly", "BetterBoatfly", "Allows boats to fly bypassing gravity checks.");
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.player.getVehicle() == null) return;

        Entity vehicle = MC.player.getVehicle();
        if (vehicle.getType().toString().contains("boat")) {
            double spd = speed.get();
            double upSpd = upSpeed.get();

            vehicle.setNoGravity(true);

            Vec3 velocity = vehicle.getDeltaMovement();
            double motionX = velocity.x;
            double motionY = 0;
            double motionZ = velocity.z;

            if (MC.options.keyJump.isDown()) {
                motionY = upSpd;
            } else if (MC.options.keySprint.isDown()) {
                motionY = -upSpd;
            }

            if (MC.options.keyUp.isDown() || MC.options.keyDown.isDown() || MC.options.keyLeft.isDown() || MC.options.keyRight.isDown()) {
                float yaw = MC.player.getYRot();
                double radians = Math.toRadians(yaw);

                motionX = -Math.sin(radians) * spd;
                motionZ = Math.cos(radians) * spd;
            } else {
                motionX = 0;
                motionZ = 0;
            }

            vehicle.setDeltaMovement(new Vec3(motionX, motionY, motionZ));
        }
    }

    @Override
    public void onDisable() {
        if (MC.player != null && MC.player.getVehicle() != null) {
            Entity vehicle = MC.player.getVehicle();
            if (vehicle.getType().toString().contains("boat")) {
                vehicle.setNoGravity(false);
            }
        }
    }
}
