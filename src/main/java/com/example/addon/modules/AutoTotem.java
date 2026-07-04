package com.example.addon.modules;

import com.example.addon.ExampleAddon;
import autismclient.modules.Module;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.mixin.accessor.AutismHandledScreenAccessor;
import autismclient.util.AutismKeyMappingBridge;
import com.example.addon.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public final class AutoTotem extends Module {
    private final DoubleSetting fastDelay = add(new DoubleSetting("fastDelay", "Fast Delay (ms)", 67.0, 0.0, 300.0, 1.0));
    private final DoubleSetting slowDelay = add(new DoubleSetting("slowDelay", "Fumble Delay (ms)", 325.0, 0.0, 800.0, 1.0));
    private final DoubleSetting fumbleChance = add(new DoubleSetting("fumbleChance", "Fumble Chance %", 55.0, 0.0, 100.0, 1.0));
    private final BoolSetting autoOpen = add(new BoolSetting("autoOpen", "Auto Open Inv", true));
    private final BoolSetting shutInventory = add(new BoolSetting("shutInventory", "Auto Close", true));
    private final BoolSetting clickSim = add(new BoolSetting("clickSim", "ClickSim", false));
    private final BoolSetting simCursor = add(new BoolSetting("simCursor", "SimCursor", false));
    private final DoubleSetting cursorSpeed = add(new DoubleSetting("cursorSpeed", "Cursor Speed", 18.0, 1.0, 100.0, 1.0));
    private final ChoiceSetting cursorMode = add(new ChoiceSetting("cursorMode", "Cursor Mode", "Smooth", "Smooth", "Heuristic", "Instant"));

    private State currentState = State.IDLE;
    private long actionTimer = -1L;
    private Slot targetSlot = null;
    private boolean openedByBot = false;
    private final Random random = new Random();

    public AutoTotem() {
        super(ExampleAddon.ID + ":autototem", "AutoTotem", "Automatically equips totems to offhand with humanized timing.");
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void tick() {
        runCycle();
    }

    private void runCycle() {
        if (MC.player == null || MC.level == null || MC.gameMode == null) {
            resetState();
            return;
        }
        if (MC.player.isSpectator() || MC.player.getHealth() <= 0.0f) {
            resetState();
            return;
        }
        if (MC.gui.screen() == null) {
            handlePlayingLogic();
        } else if (MC.gui.screen() instanceof AbstractContainerScreen) {
            handleInventoryLogic((AbstractContainerScreen<?>) MC.gui.screen());
        } else {
            resetState();
        }
    }

    private void handlePlayingLogic() {
        boolean needsRefill = MC.player.getOffhandItem().isEmpty();
        if (currentState == State.IDLE) {
            openedByBot = false;
        }
        if (autoOpen.get() && needsRefill && hasTotemInInventory()) {
            switch (currentState) {
                case IDLE:
                case COOLDOWN:
                case CLOSING:
                    scheduleNextState(State.PRE_OPENING, getBimodalDelay());
                    break;
                case PRE_OPENING:
                    if (System.currentTimeMillis() >= actionTimer) {
                        if (MC.gui.screen() == null) {
                            openedByBot = true;
                            MC.gui.setScreen(new InventoryScreen(MC.player));
                            scheduleNextState(State.OPENING_WAIT, random.nextInt(50));
                        } else {
                            resetState();
                        }
                    }
                    break;
                default:
                    resetState();
                    break;
            }
        } else {
            currentState = State.IDLE;
        }
    }

    private void handleInventoryLogic(AbstractContainerScreen<?> screen) {
        if (screen.getMenu().getCarried().isEmpty() && currentState != State.IDLE && currentState != State.SCANNING && currentState != State.OPENING_WAIT && currentState != State.PRE_OPENING && currentState != State.CLOSING && currentState != State.COOLDOWN) {
            currentState = State.IDLE;
            actionTimer = System.currentTimeMillis() + 200L;
            return;
        }
        switch (currentState) {
            case IDLE:
            case PRE_OPENING:
            case OPENING_WAIT:
                if (!isPlayerInventory(screen)) {
                    resetState();
                    return;
                }
                scheduleNextState(State.SCANNING, random.nextInt(50));
                break;
            case SCANNING:
                if (System.currentTimeMillis() < actionTimer) {
                    return;
                }
                if (!isPlayerInventory(screen)) {
                    resetState();
                    return;
                }
                if (MC.player.getOffhandItem().isEmpty()) {
                    Slot totem = findTotem(screen);
                    if (totem != null) {
                        targetSlot = totem;
                        scheduleNextState(State.SCHEDULED, getBimodalDelay());
                        return;
                    }
                }
                if (openedByBot && shutInventory.get()) {
                    scheduleNextState(State.CLOSING, 100 + random.nextInt(100));
                } else {
                    currentState = State.IDLE;
                    actionTimer = System.currentTimeMillis() + 500L;
                }
                break;
            case SCHEDULED:
                if (System.currentTimeMillis() < actionTimer) break;
                currentState = State.EXECUTING;
                break;
            case EXECUTING:
                if (performClick(screen)) {
                    scheduleNextState(State.SCANNING, 60 + random.nextInt(60));
                }
                break;
            case CLOSING:
                if (System.currentTimeMillis() < actionTimer) break;
                if (openedByBot && MC.player != null) {
                    MC.player.closeContainer();
                }
                resetState();
                break;
            case COOLDOWN:
                scheduleNextState(State.SCANNING, 10L);
                break;
        }
    }

    private boolean isPlayerInventory(AbstractContainerScreen<?> screen) {
        return screen instanceof InventoryScreen;
    }

    private boolean performClick(AbstractContainerScreen<?> screen) {
        if (targetSlot == null || MC.gameMode == null) {
            return true;
        }
        if (simCursor.get() && !moveCursorToSlot(screen, targetSlot)) {
            return false;
        }
        int syncId = MC.player.containerMenu.containerId;
        if (clickSim.get()) {
            if (!simCursor.get()) {
                MC.gameMode.handleContainerInput(syncId, targetSlot.index, 40, ContainerInput.SWAP, MC.player);
            } else {
                tapKey(MC.options.keySwapOffhand);
            }
        } else {
            MC.gameMode.handleContainerInput(syncId, targetSlot.index, 40, ContainerInput.SWAP, MC.player);
        }
        return true;
    }

    private long getBimodalDelay() {
        long delay;
        boolean isFumble = (double) random.nextInt(100) < fumbleChance.get();
        if (isFumble) {
            long mean = slowDelay.get().longValue();
            delay = (long) ((double) mean + random.nextGaussian() * 50.0);
        } else {
            long mean = fastDelay.get().longValue();
            delay = (long) ((double) mean + random.nextGaussian() * 20.0);
        }
        return Math.max(1L, Math.min(delay, 1500L));
    }

    private void scheduleNextState(State state, long delayMs) {
        this.currentState = state;
        this.actionTimer = System.currentTimeMillis() + delayMs;
    }

    private void resetState() {
        this.currentState = State.IDLE;
        this.actionTimer = -1L;
        this.targetSlot = null;
        this.openedByBot = false;
    }

    private boolean hasTotemInInventory() {
        if (MC.player == null) return false;
        for (int i = 0; i < MC.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = MC.player.getInventory().getItem(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                return true;
            }
        }
        return false;
    }

    private Slot findTotem(AbstractContainerScreen<?> screen) {
        if (simCursor.get()) {
            double cursorX = getScaledCursorX(screen.width);
            double cursorY = getScaledCursorY(screen.height);
            return findNearestTotem(screen, cursorX, cursorY);
        }
        return findFirstTotem(screen);
    }

    private Slot findFirstTotem(AbstractContainerScreen<?> screen) {
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.hasItem() || slot.getItem().getItem() != Items.TOTEM_OF_UNDYING || slot.index == 45) continue;
            return slot;
        }
        return null;
    }

    private Slot findNearestTotem(AbstractContainerScreen<?> screen, double cursorX, double cursorY) {
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        int guiX = accessor.pvpaddon$getLeftPos();
        int guiY = accessor.pvpaddon$getTopPos();
        Slot nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.hasItem() || slot.getItem().getItem() != Items.TOTEM_OF_UNDYING || slot.index == 45) continue;
            double slotCenterX = (double) (guiX + slot.x) + 8.0;
            double slotCenterY = (double) (guiY + slot.y) + 8.0;
            double dx = slotCenterX - cursorX;
            double dy = slotCenterY - cursorY;
            double distanceSq = dx * dx + dy * dy;
            if (distanceSq < nearestDistance) {
                nearestDistance = distanceSq;
                nearest = slot;
            }
        }
        return nearest;
    }

    private boolean moveCursorToSlot(AbstractContainerScreen<?> screen, Slot slot) {
        AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) screen;
        double targetX = (double) (accessor.pvpaddon$getLeftPos() + slot.x) + 8.0;
        double targetY = (double) (accessor.pvpaddon$getTopPos() + slot.y) + 8.0;
        return moveCursorToScaled(screen.width, screen.height, targetX, targetY, cursorSpeed.get(), cursorMode.get());
    }

    private void tapKey(net.minecraft.client.KeyMapping key) {
        if (key != null) {
            AutismKeyMappingBridge.of(key).autism$simulatePress(true);
            AutismKeyMappingBridge.of(key).autism$simulatePress(false);
        }
    }

    private double getScaledCursorX(int scaledWidth) {
        if (MC.getWindow() == null || scaledWidth <= 0) {
            return 0.0D;
        }
        long handle = MC.getWindow().handle();
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(handle, x, y);
        return x[0] * scaledWidth / Math.max(1, MC.getWindow().getWidth());
    }

    private double getScaledCursorY(int scaledHeight) {
        if (MC.getWindow() == null || scaledHeight <= 0) {
            return 0.0D;
        }
        long handle = MC.getWindow().handle();
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(handle, x, y);
        return y[0] * scaledHeight / Math.max(1, MC.getWindow().getHeight());
    }

    private boolean moveCursorToScaled(int scaledWidth, int scaledHeight, double targetScaledX, double targetScaledY, double speedPerTick, String mode) {
        if (MC.getWindow() == null || scaledWidth <= 0 || scaledHeight <= 0) {
            return false;
        }
        long handle = MC.getWindow().handle();
        int windowWidth = Math.max(1, MC.getWindow().getWidth());
        int windowHeight = Math.max(1, MC.getWindow().getHeight());

        double targetWindowX = targetScaledX * windowWidth / scaledWidth;
        double targetWindowY = targetScaledY * windowHeight / scaledHeight;

        double[] currentX = new double[1];
        double[] currentY = new double[1];
        GLFW.glfwGetCursorPos(handle, currentX, currentY);

        double dx = targetWindowX - currentX[0];
        double dy = targetWindowY - currentY[0];
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance <= 1.0D) {
            GLFW.glfwSetCursorPos(handle, targetWindowX, targetWindowY);
            return true;
        }

        String resolvedMode = (mode == null) ? "Smooth" : mode;
        if ("Instant".equalsIgnoreCase(resolvedMode)) {
            GLFW.glfwSetCursorPos(handle, targetWindowX, targetWindowY);
            return true;
        }

        double speed = Math.max(1.0D, speedPerTick);
        if ("Heuristic".equalsIgnoreCase(resolvedMode)) {
            if (distance > 200.0D) {
                speed *= 2.3D;
            } else if (distance > 90.0D) {
                speed *= 1.7D;
            } else if (distance > 40.0D) {
                speed *= 1.25D;
            }
        }

        double step = Math.min(distance, speed);
        double nextX = currentX[0] + dx / distance * step;
        double nextY = currentY[0] + dy / distance * step;
        GLFW.glfwSetCursorPos(handle, nextX, nextY);
        return false;
    }

    private enum State {
        IDLE,
        PRE_OPENING,
        OPENING_WAIT,
        SCANNING,
        SCHEDULED,
        EXECUTING,
        COOLDOWN,
        CLOSING
    }
}
