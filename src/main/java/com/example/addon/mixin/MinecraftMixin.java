package com.example.addon.mixin;

import com.example.addon.modules.MaceSwap;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @SuppressWarnings("unused")
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        if (MaceSwap.INSTANCE != null && MaceSwap.INSTANCE.isEnabled() && MaceSwap.INSTANCE.handleAttack()) {
            cir.setReturnValue(false);
        }
    }
}
