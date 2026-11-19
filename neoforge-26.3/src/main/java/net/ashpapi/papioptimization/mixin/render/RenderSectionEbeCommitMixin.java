package net.ashpapi.papioptimization.mixin.render;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.ashpapi.papioptimization.access.PapiEbeCompiledSection;
import net.ashpapi.papioptimization.access.PapiEbeDynamicTracker;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = SectionRenderDispatcher.RenderSection.class, priority = 1500)
public abstract class RenderSectionEbeCommitMixin {

    @Shadow
    @Final
    public AtomicReference<SectionMesh> sectionMesh;

    @Inject(
        method = "releaseSectionMesh(Lnet/minecraft/client/renderer/chunk/SectionMesh;)V",
        at = @At("HEAD")
    )
    private void papi$clearEbeBakedStateOnRelease(SectionMesh oldMesh, CallbackInfo ci) {
        if (oldMesh instanceof PapiEbeCompiledSection oldCompiled) {
            List<PapiEbeDynamicTracker> oldBaked = oldCompiled.papi$getEbeBakedTrackers();
            if (oldBaked != null) {
                for (int i = 0, n = oldBaked.size(); i < n; i++) {
                    oldBaked.get(i).papi$setEbeBaked(false);
                }
            }
        }
    }

    @Inject(method = "setSectionMesh", at = @At("RETURN"))
    private void papi$commitEbeBakedStateOnUpload(SectionMesh newMesh, CallbackInfoReturnable<SectionMesh> cir) {
        SectionMesh oldMesh = cir.getReturnValue();
        if (oldMesh != newMesh && oldMesh instanceof PapiEbeCompiledSection oldCompiled) {
            List<PapiEbeDynamicTracker> oldBaked = oldCompiled.papi$getEbeBakedTrackers();
            if (oldBaked != null) {
                for (int i = 0, n = oldBaked.size(); i < n; i++) {
                    oldBaked.get(i).papi$setEbeBaked(false);
                }
            }
        }
        if (newMesh instanceof PapiEbeCompiledSection newCompiled) {
            List<PapiEbeDynamicTracker> newBaked = newCompiled.papi$getEbeBakedTrackers();
            if (newBaked != null) {
                for (int i = 0, n = newBaked.size(); i < n; i++) {
                    newBaked.get(i).papi$setEbeBaked(true);
                }
            }
            List<PapiEbeDynamicTracker> newUnbaked = newCompiled.papi$getEbeUnbakedTrackers();
            if (newUnbaked != null) {
                for (int i = 0, n = newUnbaked.size(); i < n; i++) {
                    newUnbaked.get(i).papi$setEbeBaked(false);
                }
            }
        }
    }
}
