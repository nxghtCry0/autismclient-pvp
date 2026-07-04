package com.example.addon.modules;

import com.example.addon.ExampleAddon;
import autismclient.modules.Module;
import autismclient.mixin.accessor.AutismMovePlayerPacketAccessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public final class AntiHunger extends Module {

    public AntiHunger() {
        super(ExampleAddon.ID + ":antihunger", "AntiHunger", "Spoofs onground state to reduce hunger depletion.");
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (MC.player == null || MC.gameMode == null) return false;
        
        if (!(packet instanceof ServerboundMovePlayerPacket move)) {
            return false;
        }

        if (!MC.player.onGround() || MC.player.fallDistance > 0.5) {
            return false;
        }

        if (MC.gameMode.isDestroying()) {
            return false;
        }

        // Spoof the player as in-air to avoid hunger updates on server
        ((AutismMovePlayerPacketAccessor) move).autism$setOnGround(false);
        return false;
    }
}
