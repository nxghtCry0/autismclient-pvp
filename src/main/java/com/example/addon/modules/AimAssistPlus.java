package com.example.addon.modules;

import com.example.addon.ExampleAddon;
import autismclient.modules.Module;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.util.AutismMouseInputSimulator;
import autismclient.util.AutismRotationUtil;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class AimAssistPlus extends Module {
    private final DoubleSetting hSpeed = add(new DoubleSetting("hSpeed", "Horizontal Speed", 20.0, 1.0, 100.0, 1.0));
    private final DoubleSetting vSpeed = add(new DoubleSetting("vSpeed", "Vertical Speed", 20.0, 1.0, 100.0, 1.0));
    private final DoubleSetting fov = add(new DoubleSetting("fov", "FOV", 60.0, 0.0, 360.0, 1.0));
    private final DoubleSetting range = add(new DoubleSetting("range", "Range", 4.0, 1.0, 8.0, 0.1));

    private final ChoiceSetting targetArea = add(new ChoiceSetting("targetArea", "Target Area", "Body", "Head", "Body", "Arms", "Legs", "Nearest Part"));
    private final ChoiceSetting sortBy = add(new ChoiceSetting("sortBy", "Sort By", "Distance", "Distance", "Angle"));

    private final BoolSetting predict = add(new BoolSetting("predict", "Predict", false));
    private final DoubleSetting predictionTicks = add(new DoubleSetting("predictionTicks", "Prediction Ticks", 1.0, 0.0, 5.0, 0.1));
    private final DoubleSetting jitter = add(new DoubleSetting("jitter", "Jitter", 0.0, 0.0, 5.0, 0.1));
    private final DoubleSetting aimDeviation = add(new DoubleSetting("aimDeviation", "Aim Deviation", 0.0, 0.0, 1.0, 0.05));

    private final BoolSetting clickAim = add(new BoolSetting("clickAim", "Click Aim", false));
    private final BoolSetting weaponOnly = add(new BoolSetting("weaponOnly", "Weapon Only", false));
    private final BoolSetting horizontalOnly = add(new BoolSetting("horizontalOnly", "Horizontal Only", false));
    private final BoolSetting teamCheck = add(new BoolSetting("teamCheck", "Team Check", true));
    private final BoolSetting ignoreWalls = add(new BoolSetting("ignoreWalls", "Ignore Walls", false));
    private final BoolSetting players = add(new BoolSetting("players", "Players", true));
    private final BoolSetting hostileMobs = add(new BoolSetting("hostileMobs", "Hostile Mobs", true));
    private final BoolSetting passiveMobs = add(new BoolSetting("passiveMobs", "Passive Mobs", false));

    private Entity target;
    private Entity lastTarget;
    private Vec3 targetOffset = Vec3.ZERO;

    public AimAssistPlus() {
        super(ExampleAddon.ID + ":aimassistplus", "AimAssist+", "Automatically aims at entities with bypass simulation.");
    }

    @Override
    public void onDisable() {
        target = null;
        lastTarget = null;
        targetOffset = Vec3.ZERO;
        AutismMouseInputSimulator.clear(AutismMouseInputSimulator.Source.AIM_ASSIST);
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.level == null) return;

        if (clickAim.get() && !MC.options.keyAttack.isDown()) {
            target = null;
            lastTarget = null;
            targetOffset = Vec3.ZERO;
            AutismMouseInputSimulator.clear(AutismMouseInputSimulator.Source.AIM_ASSIST);
            return;
        }

        if (MC.gui.screen() != null || (weaponOnly.get() && !isHoldingWeapon())) {
            target = null;
            lastTarget = null;
            targetOffset = Vec3.ZERO;
            AutismMouseInputSimulator.clear(AutismMouseInputSimulator.Source.AIM_ASSIST);
            return;
        }

        updateTarget();

        if (target == null) {
            lastTarget = null;
            targetOffset = Vec3.ZERO;
            AutismMouseInputSimulator.clear(AutismMouseInputSimulator.Source.AIM_ASSIST);
            return;
        }

        if (target != lastTarget) {
            lastTarget = target;
            double dev = aimDeviation.get();
            if (dev > 0.0) {
                targetOffset = new Vec3(
                    (Math.random() - 0.5) * dev,
                    (Math.random() - 0.5) * dev,
                    (Math.random() - 0.5) * dev
                );
            } else {
                targetOffset = Vec3.ZERO;
            }
        }

        Vec3 targetPos = getTargetBonePos(target).add(targetOffset);

        if (predict.get()) {
            double predTicks = predictionTicks.get();
            Vec3 targetVel = target.getDeltaMovement();
            Vec3 playerVel = MC.player.getDeltaMovement();
            Vec3 relativeVel = targetVel.subtract(playerVel);
            targetPos = targetPos.add(relativeVel.scale(predTicks));
        }

        double deltaX = targetPos.x - MC.player.getX();
        double deltaZ = targetPos.z - MC.player.getZ();
        double deltaY = targetPos.y - (MC.player.getY() + MC.player.getEyeHeight());

        float hSpd = (float) (double) hSpeed.get();
        float vSpd = (float) (double) vSpeed.get();

        double targetYaw = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90;
        double deltaYaw = Mth.wrapDegrees(targetYaw - MC.player.getYRot());

        double horizontalDist = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double targetPitch = -Math.toDegrees(Math.atan2(deltaY, horizontalDist));
        double deltaPitch = Mth.wrapDegrees(targetPitch - MC.player.getXRot());

        if (horizontalOnly.get()) {
            deltaPitch = 0.0;
        }

        double jit = jitter.get();
        if (jit > 0.0) {
            deltaYaw += (Math.random() - 0.5) * jit;
            if (deltaPitch != 0.0) {
                deltaPitch += (Math.random() - 0.5) * jit;
            }
        }

        double hFactor = hSpd / 100.0;
        double vFactor = vSpd / 100.0;
        
        // Feed the aim rates to the client's simulator in degrees using queueRotationDelta
        AutismMouseInputSimulator.queueRotationDelta(
            AutismMouseInputSimulator.Source.AIM_ASSIST,
            (float) (deltaYaw * hFactor),
            (float) (deltaPitch * vFactor)
        );
    }

    private Vec3 getTargetBonePos(Entity ent) {
        String bodyTarget = targetArea.get();
        Vec3 entPos = ent.position();
        double eyeHeight = ent.getEyeHeight();

        if ("Head".equals(bodyTarget)) {
            return entPos.add(0, eyeHeight, 0);
        } else if ("Body".equals(bodyTarget)) {
            return entPos.add(0, eyeHeight * 0.65, 0);
        } else if ("Arms".equals(bodyTarget)) {
            float yawRad = (float) Math.toRadians(ent.getYRot());
            double ox = Math.cos(yawRad) * 0.35;
            double oz = Math.sin(yawRad) * 0.35;
            Vec3 leftArm = entPos.add(ox, eyeHeight * 0.65, oz);
            Vec3 rightArm = entPos.add(-ox, eyeHeight * 0.65, -oz);
            return getClosest2D(leftArm, rightArm);
        } else if ("Legs".equals(bodyTarget)) {
            return entPos.add(0, eyeHeight * 0.25, 0);
        } else if ("Nearest Part".equals(bodyTarget)) {
            Vec3 head = entPos.add(0, eyeHeight, 0);
            Vec3 body = entPos.add(0, eyeHeight * 0.65, 0);
            Vec3 legs = entPos.add(0, eyeHeight * 0.25, 0);
            float yawRad = (float) Math.toRadians(ent.getYRot());
            double ox = Math.cos(yawRad) * 0.35;
            double oz = Math.sin(yawRad) * 0.35;
            Vec3 leftArm = entPos.add(ox, eyeHeight * 0.65, oz);
            Vec3 rightArm = entPos.add(-ox, eyeHeight * 0.65, -oz);
            return getClosest2D(head, body, leftArm, rightArm, legs);
        }
        return entPos.add(0, eyeHeight / 2.0, 0);
    }

    private Vec3 getClosest2D(Vec3... positions) {
        Vec3 best = positions[0];
        double minDiff = Double.MAX_VALUE;
        for (Vec3 pos : positions) {
            double diff = get2DAngleDiff(pos);
            if (diff < minDiff) {
                minDiff = diff;
                best = pos;
            }
        }
        return best;
    }

    private double get2DAngleDiff(Vec3 pos) {
        Vec3 playerEye = MC.player.getEyePosition();
        Vec3 dir = pos.subtract(playerEye).normalize();
        double yaw = Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90;
        double pitch = -Math.toDegrees(Math.asin(dir.y));
        double yawDiff = Mth.wrapDegrees(yaw - MC.player.getYRot());
        double pitchDiff = Mth.wrapDegrees(pitch - MC.player.getXRot());
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }

    private void updateTarget() {
        String sort = sortBy.get();
        Entity bestEntity = null;
        double bestVal = Double.MAX_VALUE;

        for (Entity entity : MC.level.entitiesForRendering()) {
            if (isValidTarget(entity)) {
                double val;
                if ("Angle".equals(sort)) {
                    val = get2DAngleDiff(entity.position().add(0, entity.getEyeHeight() * 0.65, 0));
                } else {
                    val = MC.player.distanceTo(entity);
                }
                if (val < bestVal) {
                    bestEntity = entity;
                    bestVal = val;
                }
            }
        }
        target = bestEntity;
    }

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof LivingEntity) || entity == MC.player || !entity.isAlive()) {
            return false;
        }

        if (MC.player.distanceTo(entity) > range.get()) {
            return false;
        }

        if (!ignoreWalls.get() && !MC.player.hasLineOfSight(entity)) {
            return false;
        }

        if (entity instanceof Player p) {
            if (teamCheck.get() && MC.player.isAlliedTo(p)) {
                return false;
            }
            if (!players.get()) {
                return false;
            }
        } else {
            boolean isHostile = isHostileMob(entity);
            if (isHostile) {
                if (!hostileMobs.get()) {
                    return false;
                }
            } else {
                if (!passiveMobs.get()) {
                    return false;
                }
            }
        }

        double maxFov = fov.get();
        if (maxFov >= 360.0) return true;
        return get2DAngleDiff(entity.position().add(0, entity.getEyeHeight() * 0.65, 0)) <= maxFov / 2.0;
    }

    private boolean isHostileMob(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.monster.Monster) {
            return true;
        }
        String name = entity.getType().getDescriptionId().toLowerCase();
        return name.contains("slime") || name.contains("shulker") || name.contains("ghast") || name.contains("dragon");
    }

    private boolean isHoldingWeapon() {
        if (MC.player == null) return false;
        net.minecraft.world.item.Item item = MC.player.getMainHandItem().getItem();
        String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath().toLowerCase();
        return name.contains("sword") || name.contains("axe") || name.contains("mace") || name.contains("trident") || name.contains("bow") || name.contains("crossbow");
    }
}
