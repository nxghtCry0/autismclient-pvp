package com.example.addon.mixin;

import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Inventory.class)
public interface InventoryAccessor {
    @Accessor("selected")
    int pvpaddonGetSelected();

    @Accessor("selected")
    void pvpaddonSetSelected(int selected);
}
