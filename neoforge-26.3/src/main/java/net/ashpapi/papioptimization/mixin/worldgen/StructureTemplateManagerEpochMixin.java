package net.ashpapi.papioptimization.mixin.worldgen;

import net.ashpapi.papioptimization.worldgen.PapiStructureEpoch;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StructureTemplateManager.class, priority = 1500)
public abstract class StructureTemplateManagerEpochMixin {

    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void papi$bumpStructureEpochOnReload(ResourceManager resourceManager, CallbackInfo ci) {
        PapiStructureEpoch.bump();
    }
}
