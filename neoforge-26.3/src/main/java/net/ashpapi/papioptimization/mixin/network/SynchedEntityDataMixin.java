package net.ashpapi.papioptimization.mixin.network;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = SynchedEntityData.class, priority = 1500)
public abstract class SynchedEntityDataMixin {

    @Shadow @Final private SynchedEntityData.DataItem<?>[] itemsById;
    @Shadow private boolean isDirty;

    @Unique
    private long papi$dirtyMask = 0L;

    @Unique
    private boolean papi$hasOverflowDirty = false;

    @Inject(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V", at = @At("TAIL"))
    private <T> void papi$onSetDirty(EntityDataAccessor<T> accessor, T value, boolean force, CallbackInfo ci) {
        int id = accessor.id();
        if (id >= 0 && id < this.itemsById.length && this.itemsById[id].isDirty()) {
            if (id < 64) {
                this.papi$dirtyMask |= (1L << id);
            } else {
                this.papi$hasOverflowDirty = true;
            }
        }
    }

    @Inject(method = "assignValues", at = @At("RETURN"))
    private void papi$onAssignValues(List<SynchedEntityData.DataValue<?>> values, CallbackInfo ci) {
        if (this.isDirty) {
            this.papi$hasOverflowDirty = true;
        }
    }

    @Inject(method = "packDirty", at = @At("HEAD"), cancellable = true)
    private void papi$fastPackDirty(CallbackInfoReturnable<List<SynchedEntityData.DataValue<?>>> cir) {
        if (!this.isDirty) {
            this.papi$dirtyMask = 0L;
            this.papi$hasOverflowDirty = false;
            cir.setReturnValue(null);
            return;
        }

        if (this.papi$hasOverflowDirty || this.papi$dirtyMask == 0L) {
            this.papi$dirtyMask = 0L;
            this.papi$hasOverflowDirty = false;
            return;
        }

        long mask = this.papi$dirtyMask;
        if (mask != 0L) {
            this.papi$dirtyMask = 0L;
            this.isDirty = false;
            List<SynchedEntityData.DataValue<?>> list = new ArrayList<>(Long.bitCount(mask));

            while (mask != 0L) {
                int id = Long.numberOfTrailingZeros(mask);
                mask &= mask - 1L;
                if (id < this.itemsById.length) {
                    SynchedEntityData.DataItem<?> item = this.itemsById[id];
                    item.setDirty(false);
                    list.add(item.value());
                }
            }
            cir.setReturnValue(list);
        }
    }
}
