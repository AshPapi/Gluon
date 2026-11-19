package net.ashpapi.papioptimization.mixin.render;

import java.util.ArrayList;
import java.util.List;
import net.ashpapi.papioptimization.access.PapiEbeCompiledSection;
import net.ashpapi.papioptimization.access.PapiEbeDynamicTracker;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = CompiledSectionMesh.class, priority = 1500)
public abstract class CompiledSectionMeshEbeMixin implements PapiEbeCompiledSection {

    @Unique
    private @Nullable List<PapiEbeDynamicTracker> papi$ebeBakedTrackers;

    @Unique
    private @Nullable List<PapiEbeDynamicTracker> papi$ebeUnbakedTrackers;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void papi$copyEbeTrackersFromResults(
            TranslucencyPointOfView pov,
            SectionCompiler.Results results,
            long compileStartTimeNs,
            CallbackInfo ci
    ) {
        if ((Object) results instanceof PapiEbeCompiledSection compiled) {
            this.papi$copyEbeTrackersFrom(compiled);
        }
    }

    @Override
    public void papi$addEbeBakedTracker(PapiEbeDynamicTracker tracker) {
        if (this.papi$ebeBakedTrackers == null) {
            this.papi$ebeBakedTrackers = new ArrayList<>(4);
        }
        this.papi$ebeBakedTrackers.add(tracker);
    }

    @Override
    public void papi$addEbeUnbakedTracker(PapiEbeDynamicTracker tracker) {
        if (this.papi$ebeUnbakedTrackers == null) {
            this.papi$ebeUnbakedTrackers = new ArrayList<>(4);
        }
        this.papi$ebeUnbakedTrackers.add(tracker);
    }

    @Override
    public @Nullable List<PapiEbeDynamicTracker> papi$getEbeBakedTrackers() {
        return this.papi$ebeBakedTrackers;
    }

    @Override
    public @Nullable List<PapiEbeDynamicTracker> papi$getEbeUnbakedTrackers() {
        return this.papi$ebeUnbakedTrackers;
    }

    @Override
    public void papi$copyEbeTrackersFrom(PapiEbeCompiledSection source) {
        List<PapiEbeDynamicTracker> baked = source.papi$getEbeBakedTrackers();
        this.papi$ebeBakedTrackers = (baked != null && !baked.isEmpty()) ? new ArrayList<>(baked) : null;
        List<PapiEbeDynamicTracker> unbaked = source.papi$getEbeUnbakedTrackers();
        this.papi$ebeUnbakedTrackers = (unbaked != null && !unbaked.isEmpty()) ? new ArrayList<>(unbaked) : null;
    }
}
