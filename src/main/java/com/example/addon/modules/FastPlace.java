package com.example.addon.modules;

import com.example.addon.ExampleAddon;
import autismclient.modules.Module;
import autismclient.mixin.accessor.AutismMinecraftAccessor;

public final class FastPlace extends Module {
    public static FastPlace INSTANCE;

    public FastPlace() {
        super(ExampleAddon.ID + ":fastplace", "FastPlace", "Removes the right-click delay for placing blocks.");
        INSTANCE = this;
    }

    @Override
    public void tick() {
        if (MC.player != null) {
            ((AutismMinecraftAccessor) MC).autism$setRightClickDelay(0);
        }
    }
}
