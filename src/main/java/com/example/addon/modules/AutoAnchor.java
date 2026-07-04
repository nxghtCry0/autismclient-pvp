package com.example.addon.modules;

import com.example.addon.ExampleAddon;
import autismclient.modules.Module;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.util.AutismInventoryHelper;
import autismclient.util.AutismKeyMappingBridge;
import autismclient.util.AutismRotationUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;

import java.util.HashSet;
import java.util.Set;
import java.util.Random;

public final class AutoAnchor extends Module {
    private final BoolSetting speedMode = add(new BoolSetting("speedMode", "Speed Mode", false));
    private final DoubleSetting speedSetting = add(new DoubleSetting("speed", "Speed", 7.5, 1.0, 10.0, 0.1));
    private final BoolSetting useTotem = add(new BoolSetting("useTotem", "Use Totem", true));
    private final BoolSetting simClick = add(new BoolSetting("simClick", "Sim Click", false));
    private final BoolSetting safeMode = add(new BoolSetting("safeMode", "Safe Mode", false));
    private final BoolSetting onlyOwnAnchors = add(new BoolSetting("onlyOwnAnchors", "Only Own Anchors", true));
    private final BoolSetting silentRotation = add(new BoolSetting("silentRotation", "Silent Rotation", false));
    private final DoubleSetting rotationStrength = add(new DoubleSetting("rotationStrength", "Rotation Strength", 10.0, 1.0, 20.0, 0.1));
    private final ChoiceSetting rotPattern = add(new ChoiceSetting("rotPattern", "Pattern", "Sine", "Sine", "Smooth", "Linear", "Instant"));
    private final DoubleSetting rotJitter = add(new DoubleSetting("rotJitter", "Jitter", 0.1, 0.0, 1.0, 0.05));

    private State currentState = State.IDLE;
    private BlockPos anchorPos = null;
    private BlockPos glowstoneBlockPos = null;
    private BlockPos glowstonePlaceAgainst = null;
    private Direction glowstonePlaceDirection = null;
    private int originalSlot = -1;
    private long lastTime = 0L;
    private long glowstoneAttemptStart = 0L;
    private long lastRenderExecTime = 0L;
    private final Set<BlockPos> placedAnchors = new HashSet<>();
    private BlockState cachedAnchorState = null;
    private AutismRotationUtil.Rotation targetRotation = null;
    private final Random random = new Random();

    public AutoAnchor() {
        super(ExampleAddon.ID + ":autoanchor", "AutoAnchor", "Fills and explodes anchors with target rotation.");
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        restoreOriginalSlot();
        reset();
        placedAnchors.clear();
    }

    public void onItemUse(BlockHitResult hitResult) {
        if (!onlyOwnAnchors.get() || MC.player == null || MC.level == null) {
            return;
        }
        ItemStack held = MC.player.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.getItem() != Items.RESPAWN_ANCHOR) {
            if (held.getItem() == Items.GLOWSTONE) {
                BlockPos targetPos = hitResult.getBlockPos();
                BlockState state = MC.level.getBlockState(targetPos);
                if (state.is(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) == 4) {
                    placedAnchors.remove(targetPos);
                }
            }
            return;
        }
        BlockPos clickedPos = hitResult.getBlockPos();
        BlockState clickedState = MC.level.getBlockState(clickedPos);
        BlockPos anchorPlacementPos = clickedState.canBeReplaced() ? clickedPos : clickedPos.relative(hitResult.getDirection());
        placedAnchors.add(anchorPlacementPos);
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.level == null) {
            return;
        }

        updateRotation();

        if (!speedMode.get()) {
            updateLogic();
        } else {
            long delay = getSpeedModeDelay();
            if (System.currentTimeMillis() - lastRenderExecTime >= delay) {
                updateLogic();
                lastRenderExecTime = System.currentTimeMillis();
            }
        }
    }

    private void updateRotation() {
        if (targetRotation != null && MC.player != null) {
            AutismRotationUtil.Rotation current = AutismRotationUtil.playerRotation(MC.player);
            float strength = rotationStrength.get().floatValue();
            float horizontalFactor = strength;
            float verticalFactor = strength;
            if (rotJitter.get() > 0.0) {
                horizontalFactor += (float) (random.nextGaussian() * rotJitter.get());
                verticalFactor += (float) (random.nextGaussian() * rotJitter.get());
            }
            AutismRotationUtil.Rotation next = AutismRotationUtil.towardsLinear(current, targetRotation, horizontalFactor, verticalFactor);
            if (silentRotation.get()) {
                AutismRotationUtil.apply(MC.player, AutismRotationUtil.normalizeToSensitivity(next, current), false);
            } else {
                AutismRotationUtil.apply(MC.player, AutismRotationUtil.normalizeToSensitivity(next, current), true);
            }
        }
    }

    private long getSpeedModeDelay() {
        double speed = speedSetting.get();
        if (speed >= 10.0) {
            return 0L;
        }
        return (long) ((10.0 - speed) * 8.88888888888889);
    }

    private void updateLogic() {
        if (currentState != State.IDLE && currentState != State.COOLDOWN && timePassed(1250L)) {
            reset();
            return;
        }
        switch (currentState) {
            case IDLE: {
                HitResult hitResult = MC.hitResult;
                if (!(hitResult instanceof BlockHitResult)) {
                    return;
                }
                BlockHitResult blockHitResult = (BlockHitResult) hitResult;
                BlockPos targetPos = blockHitResult.getBlockPos();
                if (onlyOwnAnchors.get() && !placedAnchors.contains(targetPos)) {
                    return;
                }
                if (!isNewAnchor(targetPos)) break;
                anchorPos = targetPos;
                originalSlot = MC.player.getInventory().getSelectedSlot();
                resetTimer();
                startRotatingToAnchor();
                currentState = State.ROTATING_TO_FILL;
                break;
            }
            case ROTATING_TO_FILL: {
                if (!isAnchorStillValid()) {
                    reset();
                    return;
                }
                if (!timePassed(50L) && !isRotationComplete()) break;
                currentState = State.FILL_ANCHOR;
                resetTimer();
                break;
            }
            case FILL_ANCHOR: {
                if (!isAnchorStillValid()) {
                    reset();
                    return;
                }
                int glowstoneSlot = findItemInHotbar(Items.GLOWSTONE);
                if (glowstoneSlot != -1) {
                    AutismInventoryHelper.selectHotbarSlot(MC, glowstoneSlot);
                    simClickUseKey();
                    currentState = State.WAITING_FOR_FILL;
                    resetTimer();
                    break;
                }
                reset();
                break;
            }
            case WAITING_FOR_FILL: {
                if (!isAnchorStillValid()) {
                    reset();
                    return;
                }
                if (cachedAnchorState.getValue(RespawnAnchorBlock.CHARGE) <= 0) break;
                if (shouldUseSafeMode()) {
                    currentState = State.ROTATING_TO_GLOWSTONE;
                    glowstoneAttemptStart = System.currentTimeMillis();
                } else {
                    currentState = State.PREPARE_TO_EXPLODE;
                }
                resetTimer();
                break;
            }
            case ROTATING_TO_GLOWSTONE: {
                if (!isAnchorStillValid()) {
                    reset();
                    return;
                }
                if (System.currentTimeMillis() - glowstoneAttemptStart > 200L) {
                    resetTimer();
                    currentState = State.PREPARE_TO_EXPLODE;
                    break;
                }
                Vec3 playerPos = MC.player.position();
                Vec3 anchorCenter = new Vec3(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5);
                Vec3 midpoint = playerPos.add(anchorCenter).scale(0.5);
                glowstoneBlockPos = new BlockPos((int) Math.floor(midpoint.x), anchorPos.getY(), (int) Math.floor(midpoint.z));
                BlockState glowstoneBlockState = MC.level.getBlockState(glowstoneBlockPos);
                if (glowstoneBlockState.canBeReplaced()) {
                    int glowstoneBlockSlot = findItemInHotbar(Items.GLOWSTONE);
                    if (glowstoneBlockSlot != -1) {
                        AutismInventoryHelper.selectHotbarSlot(MC, glowstoneBlockSlot);
                        glowstonePlaceAgainst = findAdjacentSolidBlock(glowstoneBlockPos);
                        if (glowstonePlaceAgainst == null) break;
                        int dx = glowstoneBlockPos.getX() - glowstonePlaceAgainst.getX();
                        int dy = glowstoneBlockPos.getY() - glowstonePlaceAgainst.getY();
                        int dz = glowstoneBlockPos.getZ() - glowstonePlaceAgainst.getZ();
                        glowstonePlaceDirection = Direction.getNearest(dx, dy, dz, Direction.UP);
                        if (glowstonePlaceDirection == null) break;
                        startRotatingToGlowstoneBlock(glowstonePlaceAgainst);
                        currentState = State.PLACE_GLOWSTONE_BLOCK;
                        resetTimer();
                        return;
                    }
                    resetTimer();
                    currentState = State.PREPARE_TO_EXPLODE;
                    break;
                }
                resetTimer();
                currentState = State.ROTATING_TO_ANCHOR;
                startRotatingToAnchor();
                break;
            }
            case PLACE_GLOWSTONE_BLOCK: {
                if (!isAnchorStillValid()) {
                    reset();
                    return;
                }
                if (!timePassed(50L) && !isRotationComplete()) break;
                if (glowstonePlaceAgainst != null && glowstonePlaceDirection != null) {
                    simClickUseKey();
                    resetTimer();
                    currentState = State.ROTATING_TO_ANCHOR;
                    startRotatingToAnchor();
                    break;
                }
                resetTimer();
                currentState = State.PREPARE_TO_EXPLODE;
                break;
            }
            case ROTATING_TO_ANCHOR: {
                if (!isAnchorStillValid()) {
                    reset();
                    return;
                }
                if (!timePassed(50L) && !isRotationComplete()) break;
                currentState = State.PREPARE_TO_EXPLODE;
                resetTimer();
                break;
            }
            case PREPARE_TO_EXPLODE: {
                if (!isAnchorStillValid()) {
                    reset();
                    return;
                }
                int slotToSwitchTo = useTotem.get() ? findItemInHotbar(Items.TOTEM_OF_UNDYING) : -1;
                if (slotToSwitchTo == -1) {
                    slotToSwitchTo = findEmptyHotbarSlot();
                }
                if (slotToSwitchTo != -1) {
                    AutismInventoryHelper.selectHotbarSlot(MC, slotToSwitchTo);
                    startRotatingToAnchor();
                    currentState = State.ROTATING_TO_EXPLODE;
                    resetTimer();
                    break;
                }
                reset();
                break;
            }
            case ROTATING_TO_EXPLODE: {
                if (!isAnchorStillValid()) {
                    reset();
                    return;
                }
                if (!timePassed(50L) && !isRotationComplete()) break;
                currentState = State.ARMED;
                resetTimer();
                break;
            }
            case ARMED: {
                if (!isAnchorStillValid()) {
                    reset();
                    return;
                }
                simClickUseKey();
                if (anchorPos != null) {
                    placedAnchors.remove(anchorPos);
                }
                targetRotation = null;
                resetTimer();
                currentState = State.COOLDOWN;
                break;
            }
            case COOLDOWN: {
                if (timePassed(50L) && originalSlot != -1) {
                    restoreOriginalSlot();
                }
                if (!timePassed(400L)) break;
                reset();
            }
        }
    }

    private boolean shouldUseSimClick() {
        return !speedMode.get() && simClick.get();
    }

    private boolean shouldUseSafeMode() {
        return !speedMode.get() && safeMode.get();
    }

    private void resetTimer() {
        lastTime = System.currentTimeMillis();
    }

    private boolean timePassed(long milliseconds) {
        return System.currentTimeMillis() - lastTime >= milliseconds;
    }

    private void startRotatingToAnchor() {
        if (anchorPos != null && MC.player != null) {
            Vec3 targetPoint = findVisibleAnchorPoint();
            Vec3 eyes = MC.player.getEyePosition();
            targetRotation = AutismRotationUtil.lookingAt(targetPoint, eyes);
        }
    }

    private void startRotatingToGlowstoneBlock(BlockPos targetBlock) {
        if (targetBlock != null && MC.player != null) {
            Vec3 targetPoint = new Vec3(targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5);
            Vec3 eyes = MC.player.getEyePosition();
            targetRotation = AutismRotationUtil.lookingAt(targetPoint, eyes);
        }
    }

    private Vec3 findVisibleAnchorPoint() {
        if (anchorPos == null) {
            return Vec3.ZERO;
        }
        if (glowstoneBlockPos != null) {
            return new Vec3(anchorPos.getX() + 0.5, anchorPos.getY() + 0.9, anchorPos.getZ() + 0.5);
        }
        return new Vec3(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5);
    }

    private void simClickUseKey() {
        if (shouldUseSimClick()) {
            AutismKeyMappingBridge.of(MC.options.keyUse).autism$simulatePress(true);
            AutismKeyMappingBridge.of(MC.options.keyUse).autism$simulatePress(false);
        } else {
            if (MC.player != null && MC.gameMode != null) {
                MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
            }
        }
    }

    private boolean isNewAnchor(BlockPos pos) {
        if (MC.player == null || MC.level == null) return false;
        if (MC.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) >= 25.0) {
            return false;
        }
        BlockState state = MC.level.getBlockState(pos);
        return state.is(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) == 0;
    }

    private boolean isAnchorStillValid() {
        if (anchorPos == null || MC.player == null || MC.level == null) {
            return false;
        }
        if (MC.player.distanceToSqr(anchorPos.getX() + 0.5, anchorPos.getY() + 0.5, anchorPos.getZ() + 0.5) >= 25.0) {
            return false;
        }
        cachedAnchorState = MC.level.getBlockState(anchorPos);
        return cachedAnchorState.is(Blocks.RESPAWN_ANCHOR);
    }

    private boolean isRotationComplete() {
        if (anchorPos == null || MC.player == null) {
            return false;
        }
        if (targetRotation == null) {
            return true;
        }
        AutismRotationUtil.Rotation current = AutismRotationUtil.playerRotation(MC.player);
        return AutismRotationUtil.angleTo(current, targetRotation) < 3.0f;
    }

    private int findItemInHotbar(net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; ++i) {
            if (MC.player.getInventory().getItem(i).getItem() != item) continue;
            return i;
        }
        return -1;
    }

    private int findEmptyHotbarSlot() {
        for (int i = 0; i < 9; ++i) {
            if (!MC.player.getInventory().getItem(i).isEmpty()) continue;
            return i;
        }
        return -1;
    }

    private BlockPos findAdjacentSolidBlock(BlockPos pos) {
        if (MC.level == null) return null;
        for (Direction dir : Direction.values()) {
            BlockPos adjacentPos = pos.relative(dir);
            if (MC.level.getBlockState(adjacentPos).canBeReplaced()) continue;
            return adjacentPos;
        }
        return null;
    }

    private void restoreOriginalSlot() {
        if (MC.player != null && originalSlot != -1) {
            AutismInventoryHelper.selectHotbarSlot(MC, originalSlot);
            originalSlot = -1;
        }
    }

    private void reset() {
        targetRotation = null;
        restoreOriginalSlot();
        currentState = State.IDLE;
        anchorPos = null;
        glowstoneBlockPos = null;
        glowstonePlaceAgainst = null;
        glowstonePlaceDirection = null;
        cachedAnchorState = null;
        resetTimer();
        glowstoneAttemptStart = 0L;
        lastRenderExecTime = 0L;
    }

    private enum State {
        IDLE,
        ROTATING_TO_FILL,
        FILL_ANCHOR,
        WAITING_FOR_FILL,
        ROTATING_TO_GLOWSTONE,
        PLACE_GLOWSTONE_BLOCK,
        ROTATING_TO_ANCHOR,
        PREPARE_TO_EXPLODE,
        ROTATING_TO_EXPLODE,
        ARMED,
        COOLDOWN
    }
}
