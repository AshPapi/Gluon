package net.ashpapi.papioptimization.mixin.physics;

import net.ashpapi.papioptimization.physics.PapiVoxelShapeExtension;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(value = VoxelShape.class, priority = 1500)
public abstract class VoxelShapeCacheMixin implements PapiVoxelShapeExtension {

    @Unique
    private volatile List<AABB> papi$cachedAabbs = null;

    @Unique
    private volatile @Nullable AABB papi$singleBox = null;

    @Unique
    private volatile byte papi$singleBoxState = 0;

    @Inject(method = "toAabbs", at = @At("HEAD"), cancellable = true)
    private void papi$getCachedAabbs(CallbackInfoReturnable<List<AABB>> cir) {
        if (this.papi$cachedAabbs != null) {
            cir.setReturnValue(this.papi$cachedAabbs);
        }
    }

    @Inject(method = "toAabbs", at = @At("RETURN"), cancellable = true)
    private void papi$setCachedAabbs(CallbackInfoReturnable<List<AABB>> cir) {
        if (this.papi$cachedAabbs == null) {
            List<AABB> res = cir.getReturnValue();
            if (res != null) {
                if (res.size() == 1) {
                    this.papi$singleBox = res.get(0);
                    this.papi$singleBoxState = 1;
                } else {
                    this.papi$singleBox = null;
                    this.papi$singleBoxState = 2;
                }
                List<AABB> immutableCopy = List.copyOf(res);
                this.papi$cachedAabbs = immutableCopy;
                cir.setReturnValue(immutableCopy);
            }
        }
    }

    @Inject(method = "move(DDD)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("RETURN"))
    private void papi$propagateMovedSingleBox(double x, double y, double z, CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape moved = cir.getReturnValue();
        if (moved != null && moved != (Object) this) {
            byte state = this.papi$singleBoxState;
            if (state == 1) {
                AABB box = this.papi$singleBox;
                if (box != null) {
                    ((PapiVoxelShapeExtension) moved).papi$setSingleBoxState(box.move(x, y, z), (byte) 1);
                }
            } else if (state == 2) {
                ((PapiVoxelShapeExtension) moved).papi$setSingleBoxState(null, (byte) 2);
            }
        }
    }

    @Override
    public void papi$setSingleBoxState(@Nullable AABB box, byte state) {
        this.papi$singleBox = box;
        this.papi$singleBoxState = state;
    }

    @Override
    public @Nullable AABB papi$getSingleBox() {
        if (this.papi$singleBoxState == 0) {
            ((VoxelShape) (Object) this).toAabbs();
        }
        return this.papi$singleBox;
    }

    @Override
    public boolean papi$isSingleBox() {
        if (this.papi$singleBoxState == 0) {
            ((VoxelShape) (Object) this).toAabbs();
        }
        return this.papi$singleBoxState == 1;
    }
}
