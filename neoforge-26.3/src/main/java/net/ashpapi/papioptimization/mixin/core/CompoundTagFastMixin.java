package net.ashpapi.papioptimization.mixin.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(value = CompoundTag.class, priority = 1500)
public abstract class CompoundTagFastMixin {

    @Shadow @Final private Map<String, Tag> tags;

    @Inject(method = "copy()Lnet/minecraft/nbt/CompoundTag;", at = @At("HEAD"), cancellable = true)
    private void papi$fastCopy(CallbackInfoReturnable<CompoundTag> cir) {
        if (this.tags.isEmpty()) {
            cir.setReturnValue(new CompoundTag());
        }
    }
}
