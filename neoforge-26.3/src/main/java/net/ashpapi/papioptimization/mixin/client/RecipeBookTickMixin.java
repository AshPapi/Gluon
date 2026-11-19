package net.ashpapi.papioptimization.mixin.client;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RecipeBookComponent.class, priority = 1500)
public abstract class RecipeBookTickMixin {
    @Shadow public abstract boolean isVisible();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void papi$skipRecipeBookTickWhenClosed(CallbackInfo ci) {
        if (!this.isVisible()) {
            ci.cancel();
        }
    }
}
