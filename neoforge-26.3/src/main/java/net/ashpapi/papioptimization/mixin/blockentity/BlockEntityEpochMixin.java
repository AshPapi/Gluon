package net.ashpapi.papioptimization.mixin.blockentity;

import net.ashpapi.papioptimization.access.PapiEbeDynamicTracker;
import net.ashpapi.papioptimization.access.PapiEpochContainer;
import net.ashpapi.papioptimization.access.PapiRenderBoxCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockEntity.class, priority = 1500)
public abstract class BlockEntityEpochMixin implements PapiRenderBoxCache, PapiEbeDynamicTracker {

    @Unique
    private @Nullable AABB papi$cachedChestBox;

    @Unique
    private @Nullable AABB papi$cachedShulkerBox;

    @Unique
    private long papi$cachedBoxPos = Long.MIN_VALUE;

    @Unique
    private volatile boolean papi$ebeDynamic;

    @Unique
    private volatile boolean papi$ebeBaked;

    @Unique
    private long papi$ebeCloseGraceUntil = Long.MIN_VALUE;

    @Inject(method = "setChanged()V", at = @At("HEAD"))
    private void papi$onSetChanged(CallbackInfo ci) {
        if ((Object) this instanceof PapiEpochContainer epochContainer) {
            epochContainer.papi$incrementEpoch();
        }
    }

    @Override
    public AABB papi$getOrComputeChestBox(BlockPos pos) {
        long posLong = pos.asLong();
        if (posLong != this.papi$cachedBoxPos) {
            this.papi$cachedBoxPos = posLong;
            this.papi$cachedChestBox = null;
            this.papi$cachedShulkerBox = null;
        } else if (this.papi$cachedChestBox != null) {
            return this.papi$cachedChestBox;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        AABB box = new AABB(x - 1, y, z - 1, x + 2, y + 2, z + 2);
        this.papi$cachedChestBox = box;
        return box;
    }

    @Override
    public AABB papi$getOrComputeShulkerBox(BlockPos pos) {
        long posLong = pos.asLong();
        if (posLong != this.papi$cachedBoxPos) {
            this.papi$cachedBoxPos = posLong;
            this.papi$cachedChestBox = null;
            this.papi$cachedShulkerBox = null;
        } else if (this.papi$cachedShulkerBox != null) {
            return this.papi$cachedShulkerBox;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        AABB box = new AABB(x - 0.5, y - 0.5, z - 0.5, x + 1.5, y + 1.5, z + 1.5);
        this.papi$cachedShulkerBox = box;
        return box;
    }

    @Override
    public boolean papi$wasEbeDynamic() {
        return this.papi$ebeDynamic;
    }

    @Override
    public void papi$setEbeDynamic(boolean dynamic) {
        this.papi$ebeDynamic = dynamic;
    }

    @Override
    public long papi$getEbeCloseGraceUntil() {
        return this.papi$ebeCloseGraceUntil;
    }

    @Override
    public void papi$setEbeCloseGraceUntil(long gameTime) {
        this.papi$ebeCloseGraceUntil = gameTime;
    }

    @Override
    public boolean papi$isEbeBaked() {
        return this.papi$ebeBaked;
    }

    @Override
    public void papi$setEbeBaked(boolean baked) {
        this.papi$ebeBaked = baked;
    }
}
