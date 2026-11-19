package net.ashpapi.papioptimization.mixin.blockentity;

import net.ashpapi.papioptimization.access.PapiEpochContainer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CompoundContainer.class, priority = 1500)
public abstract class CompoundContainerEpochMixin implements PapiEpochContainer {

    @Shadow @Final private Container container1;
    @Shadow @Final private Container container2;

    @Unique
    private long papi$compoundEpoch = 1L;

    @Override
    public long papi$getChangeEpoch() {
        long e = this.papi$compoundEpoch;
        if (this.container1 instanceof PapiEpochContainer c1) {
            e += c1.papi$getChangeEpoch() * 31L;
        }
        if (this.container2 instanceof PapiEpochContainer c2) {
            e += c2.papi$getChangeEpoch() * 1315423911L;
        }
        return e;
    }

    @Override
    public void papi$incrementEpoch() {
        this.papi$compoundEpoch++;
    }

    @Inject(method = "setChanged", at = @At("HEAD"))
    private void papi$onSetChanged(CallbackInfo ci) {
        this.papi$compoundEpoch++;
    }
}
