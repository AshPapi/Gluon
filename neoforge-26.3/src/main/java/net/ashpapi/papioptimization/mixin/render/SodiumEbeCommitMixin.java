package net.ashpapi.papioptimization.mixin.render;

import java.util.List;
import net.ashpapi.papioptimization.client.ebe.PapiSodiumEbeBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", priority = 1500, remap = false)
public abstract class SodiumEbeCommitMixin {

    @Inject(
        method = "updateWithResult(Lnet/caffeinemc/mods/sodium/client/render/viewport/Viewport;Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;Ljava/util/List;)I",
        at = @At("RETURN"),
        remap = false,
        require = 0
    )
    private void papi$commitSodiumEbeOnUpload(
            @Coerce Object viewport,
            @Coerce Object section,
            @Coerce Object buildOutput,
            List<?> immediateList,
            CallbackInfoReturnable<Integer> cir
    ) {
        PapiSodiumEbeBridge.commitBuildOutput(section, buildOutput);
    }

    @Inject(method = "onSectionRemoved(III)V", at = @At("HEAD"), remap = false, require = 0)
    private void papi$clearSodiumEbeOnSectionRemoved(int x, int y, int z, CallbackInfo ci) {
        PapiSodiumEbeBridge.clearSection(x, y, z);
    }

    @Inject(method = "destroy()V", at = @At("HEAD"), remap = false, require = 0)
    private void papi$clearSodiumEbeOnDestroy(CallbackInfo ci) {
        PapiSodiumEbeBridge.clearPending();
    }
}
