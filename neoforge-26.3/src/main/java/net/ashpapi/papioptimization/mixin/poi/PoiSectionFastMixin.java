package net.ashpapi.papioptimization.mixin.poi;

import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.ashpapi.papioptimization.access.PapiPoiRecordOwner;
import net.ashpapi.papioptimization.access.PapiPoiSectionFast;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PoiSection.class, priority = 1500)
public abstract class PoiSectionFastMixin implements PapiPoiSectionFast {

    @Shadow
    @Final
    private Short2ObjectMap<PoiRecord> records;

    @Unique
    private boolean papi$occupancyDirty = true;

    @Unique
    private boolean papi$hasAnySpace;

    @Unique
    private boolean papi$hasAnyOccupied;

    @Unique
    private void papi$recomputeOccupancy() {
        boolean anySpace = false;
        boolean anyOccupied = false;
        if (!this.records.isEmpty()) {
            for (PoiRecord rec : this.records.values()) {
                if (rec.hasSpace()) {
                    anySpace = true;
                }
                if (rec.isOccupied()) {
                    anyOccupied = true;
                }
                if (anySpace && anyOccupied) {
                    break;
                }
            }
        }
        this.papi$hasAnySpace = anySpace;
        this.papi$hasAnyOccupied = anyOccupied;
        this.papi$occupancyDirty = false;
    }

    @Override
    public boolean papi$isEmpty() {
        return this.records.isEmpty();
    }

    @Override
    public boolean papi$mayMatchOccupancy(PoiManager.Occupancy occupancy) {
        if (this.records.isEmpty()) {
            return false;
        }
        if (occupancy == PoiManager.Occupancy.ANY) {
            return true;
        }
        if (this.papi$occupancyDirty) {
            papi$recomputeOccupancy();
        }
        if (occupancy == PoiManager.Occupancy.HAS_SPACE) {
            return this.papi$hasAnySpace;
        }
        if (occupancy == PoiManager.Occupancy.IS_OCCUPIED) {
            return this.papi$hasAnyOccupied;
        }
        return true;
    }

    @Override
    public void papi$markOccupancyDirty() {
        this.papi$occupancyDirty = true;
    }

    @Inject(method = "add(Lnet/minecraft/world/entity/ai/village/poi/PoiRecord;)Z", at = @At("RETURN"))
    private void papi$onAddRecord(PoiRecord record, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && record != null) {
            ((PapiPoiRecordOwner) record).papi$setOwnerSection(this);
            this.papi$occupancyDirty = true;
        }
    }

    @Inject(method = "remove", at = @At("RETURN"))
    private void papi$onRemove(BlockPos pos, CallbackInfo ci) {
        this.papi$occupancyDirty = true;
    }

    @Inject(method = "release", at = @At("RETURN"))
    private void papi$onRelease(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            this.papi$occupancyDirty = true;
        }
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void papi$onClear(CallbackInfo ci) {
        if (!this.records.isEmpty()) {
            for (PoiRecord rec : this.records.values()) {
                ((PapiPoiRecordOwner) rec).papi$setOwnerSection(null);
            }
        }
        this.papi$hasAnySpace = false;
        this.papi$hasAnyOccupied = false;
        this.papi$occupancyDirty = false;
    }

    @Inject(method = "refresh", at = @At("RETURN"))
    private void papi$onRefresh(Consumer<BiConsumer<BlockPos, Holder<PoiType>>> consumer, CallbackInfo ci) {
        if (!this.records.isEmpty()) {
            for (PoiRecord rec : this.records.values()) {
                ((PapiPoiRecordOwner) rec).papi$setOwnerSection(this);
            }
        }
        this.papi$occupancyDirty = true;
    }

    @Inject(method = "getRecords", at = @At("HEAD"), cancellable = true)
    private void papi$fastEmptyGetRecords(
            Predicate<Holder<PoiType>> typePredicate,
            PoiManager.Occupancy occupancy,
            CallbackInfoReturnable<Stream<PoiRecord>> cir
    ) {
        if (!papi$mayMatchOccupancy(occupancy)) {
            cir.setReturnValue(Stream.empty());
        }
    }

    @Inject(method = "exists", at = @At("HEAD"), cancellable = true)
    private void papi$fastEmptyExists(BlockPos pos, Predicate<Holder<PoiType>> predicate, CallbackInfoReturnable<Boolean> cir) {
        if (this.records.isEmpty()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getFreeTickets", at = @At("HEAD"), cancellable = true)
    private void papi$fastEmptyFreeTickets(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (this.records.isEmpty()) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "getType", at = @At("HEAD"), cancellable = true)
    private void papi$fastEmptyGetType(BlockPos pos, CallbackInfoReturnable<Optional<Holder<PoiType>>> cir) {
        if (this.records.isEmpty()) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
