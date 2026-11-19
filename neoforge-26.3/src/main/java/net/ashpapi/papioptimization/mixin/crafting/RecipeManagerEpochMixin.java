package net.ashpapi.papioptimization.mixin.crafting;

import net.ashpapi.papioptimization.core.PapiRecipeEpoch;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RecipeManager.class, priority = 1500)
public abstract class RecipeManagerEpochMixin {

    @Inject(method = "finalizeRecipeLoading", at = @At("TAIL"))
    private void papi$bumpRecipeEpochOnReload(FeatureFlagSet enabledFeatures, CallbackInfo ci) {
        PapiRecipeEpoch.bump();
    }
}
