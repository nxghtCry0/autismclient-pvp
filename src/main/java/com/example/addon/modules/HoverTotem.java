package com.example.addon.modules;

import com.example.addon.ExampleAddon;
import autismclient.modules.Module;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.DoubleSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.ChoiceSetting;
import autismclient.mixin.accessor.AutismHandledScreenAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.List;

public final class HoverTotem extends Module {
    public enum Mode { BOTH, OFFHAND, DHAND }
    public enum DhandMode { SMART, MANUAL }

    private final ChoiceSetting mode = add(new ChoiceSetting("mode", "Mode", "Both", "Both", "Offhand", "Dhand"));
    private final DoubleSetting speed = add(new DoubleSetting("speed", "Speed (Delay)", 0.0, 0.0, 10.0, 0.1));
    private final BoolSetting shutInventory = add(new BoolSetting("shutInventory", "Shut Inventory", true));
    private final ChoiceSetting dhandMode = add(new ChoiceSetting("dhandMode", "Dhand Mode", "Smart", "Smart", "Manual"));
    private final IntSetting manualSlot = add(new IntSetting("manualSlot", "Manual Slot", 0, 0, 8, 1));

    private long executionTime = -1L;
    private Slot targetSlot = null;
    private ActionType pendingAction = null;
    private int swapTargetIndex = -1;

    private static final List<Item> GARBAGE_ITEMS = Arrays.asList(
        Items.COBBLESTONE, Items.DIRT, Items.GRAVEL, Items.NETHERRACK, Items.SAND, Items.SOUL_SAND
    );

    public HoverTotem() {
        super(ExampleAddon.ID + ":hovertotem", "HoverTotem", "Hovering totems equips or moves them.");
    }

    @Override
    public void onDisable() {
        resetAction();
    }

    @Override
    public void tick() {
        if (executionTime != -1L) {
            if (System.currentTimeMillis() >= executionTime) {
                performAction();
            }
            return;
        }

        if (MC.player == null || MC.gameMode == null || MC.gui.screen() == null || !(MC.gui.screen() instanceof AbstractContainerScreen)) {
            resetAction();
            return;
        }

        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) MC.gui.screen();
        Slot hoveredSlot = ((AutismHandledScreenAccessor) screen).getFocusedSlot();

        if (hoveredSlot == null || !hoveredSlot.hasItem() || hoveredSlot.getItem().getItem() != Items.TOTEM_OF_UNDYING) {
            return;
        }

        if (isOffhandSlot(hoveredSlot)) {
            return;
        }

        boolean offhandEmpty = MC.player.getOffhandItem().isEmpty();
        boolean hotbarFull = isHotbarFull();
        boolean inHotbar = isSlotInHotbar(hoveredSlot);
        String currentMode = mode.get();

        if ((currentMode.equals("Both") || currentMode.equals("Offhand")) && offhandEmpty) {
            scheduleAction(hoveredSlot, ActionType.OFFHAND_SWAP);
            return;
        }

        if ((currentMode.equals("Both") || currentMode.equals("Dhand")) && !inHotbar) {
            if (!hotbarFull) {
                scheduleAction(hoveredSlot, ActionType.HOTBAR_QUICK_MOVE);
            } else {
                String dhandModeValue = dhandMode.get();
                if (dhandModeValue.equals("Smart")) {
                    int garbageIndex = getGarbageSlotIndex();
                    if (garbageIndex != -1) {
                        swapTargetIndex = garbageIndex;
                        scheduleAction(hoveredSlot, ActionType.HOTBAR_SWAP);
                    }
                } else if (dhandModeValue.equals("Manual")) {
                    int targetIndex = manualSlot.get();
                    ItemStack targetStack = MC.player.getInventory().getItem(targetIndex);
                    if (targetStack.getItem() != Items.TOTEM_OF_UNDYING) {
                        swapTargetIndex = targetIndex;
                        scheduleAction(hoveredSlot, ActionType.HOTBAR_SWAP);
                    }
                }
            }
        }
    }

    private void scheduleAction(Slot slot, ActionType action) {
        this.targetSlot = slot;
        this.pendingAction = action;
        long delayMs = (long) (speed.get() * 50.0);
        this.executionTime = System.currentTimeMillis() + delayMs;
    }

    private void performAction() {
        if (targetSlot == null || pendingAction == null || MC.player == null || MC.gameMode == null) {
            resetAction();
            return;
        }
        int syncId = MC.player.containerMenu.containerId;
        switch (pendingAction) {
            case OFFHAND_SWAP:
                MC.gameMode.handleContainerInput(syncId, targetSlot.index, 40, ContainerInput.SWAP, MC.player);
                break;
            case HOTBAR_QUICK_MOVE:
                MC.gameMode.handleContainerInput(syncId, targetSlot.index, 0, ContainerInput.QUICK_MOVE, MC.player);
                break;
            case HOTBAR_SWAP:
                if (swapTargetIndex != -1) {
                    MC.gameMode.handleContainerInput(syncId, targetSlot.index, swapTargetIndex, ContainerInput.SWAP, MC.player);
                }
                break;
        }
        if (shutInventory.get()) {
            MC.player.closeContainer();
        }
        resetAction();
    }

    private void resetAction() {
        this.executionTime = -1L;
        this.targetSlot = null;
        this.pendingAction = null;
        this.swapTargetIndex = -1;
    }

    private boolean isHotbarFull() {
        if (MC.player == null) return true;
        for (int i = 0; i < 9; ++i) {
            if (MC.player.getInventory().getItem(i).isEmpty()) return false;
        }
        return true;
    }

    private boolean isSlotInHotbar(Slot slot) {
        return slot.container == MC.player.getInventory() && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < 9;
    }

    private boolean isOffhandSlot(Slot slot) {
        // Offhand slot in player inventory screen is at index 45
        return slot.index == 45;
    }

    private int getGarbageSlotIndex() {
        if (MC.player == null) return -1;
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = MC.player.getInventory().getItem(i);
            if (stack.isEmpty() || GARBAGE_ITEMS.contains(stack.getItem())) {
                return i;
            }
        }
        return -1;
    }

    private enum ActionType {
        OFFHAND_SWAP,
        HOTBAR_QUICK_MOVE,
        HOTBAR_SWAP
    }
}
