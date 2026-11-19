package net.ashpapi.papioptimization.mixin.poi;

import net.ashpapi.papioptimization.access.PapiPoiRecordOwner;
import net.ashpapi.papioptimization.access.PapiPoiSectionFast;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PoiRecord.class, priority = 1500)
public abstract class PoiRecordFastMixin implements PapiPoiRecordOwner {

    @Unique
    private @Nullable PapiPoiSectionFast papi$ownerSection;

    @Override
    public void papi$setOwnerSection(@Nullable PapiPoiSectionFast section) {
        this.papi$ownerSection = section;
    }

    @Inject(method = "acquireTicket", at = @At("RETURN"))
    private void papi$onAcquireTicket(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && this.papi$ownerSection != null) {
            this.papi$ownerSection.papi$markOccupancyDirty();
        }
    }

    @Inject(method = "releaseTicket", at = @At("RETURN"))
    private void papi$onReleaseTicket(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && this.papi$ownerSection != null) {
            this.papi$ownerSection.papi$markOccupancyDirty();
        }
    }
}
