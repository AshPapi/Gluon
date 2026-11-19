package net.ashpapi.papioptimization.mixin.projectile;

import net.ashpapi.papioptimization.mixin.entity.EntityInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractArrow.class, priority = 1500)
public abstract class AbstractArrowFastMixin {

    @Shadow protected abstract boolean isInGround();
    @Shadow public abstract boolean isNoPhysics();
    @Shadow public int shakeTime;
    @Shadow private @Nullable BlockState lastState;
    @Shadow protected int inGroundTime;

    @Shadow protected abstract void tickDespawn();
    @Shadow protected abstract boolean shouldFall();
    @Shadow protected abstract void startFalling();

    /**
     * Fast-path for grounded arrows:
     * When an arrow is already stuck in the ground, bypass redundant VoxelShape.toAabbs()
     * collision querying and unnecessary fluid interaction checks when not on fire.
     * Also caps stuck disallowed arrows in skeleton mob farms.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void papi$fastGroundedTick(CallbackInfo ci) {
        AbstractArrow arrow = (AbstractArrow) (Object) this;
        if (!((Object) this instanceof net.minecraft.world.entity.projectile.arrow.Arrow
                || (Object) this instanceof net.minecraft.world.entity.projectile.arrow.SpectralArrow)) {
            return;
        }
        if (arrow.getRemainingFireTicks() > 0 || arrow.isOnFire() || arrow.tickCount < 2
                || ((this.inGroundTime + arrow.getId()) & 3) == 0
                || (arrow.portalProcess != null && arrow.portalProcess.isInsidePortalThisTick())) {
            return;
        }

        if (this.isInGround() && !this.isNoPhysics()) {
            arrow.checkBelowWorld();

            if (this.shakeTime > 0) {
                --this.shakeTime;
            }

            if (!arrow.level().isClientSide()) {
                BlockPos blockPos = arrow.blockPosition();
                BlockState blockState = arrow.level().getBlockState(blockPos);

                if (this.lastState != blockState && this.shouldFall()) {
                    this.startFalling();
                } else {
                    this.tickDespawn();
                }
            }

            ++this.inGroundTime;

            if (arrow.isAlive()) {
                ((EntityInvoker) arrow).papi$applyEffectsFromBlocks();
            }

            if (!arrow.level().isClientSide()) {
                arrow.setSharedFlagOnFire(arrow.getRemainingFireTicks() > 0);
            }

            ci.cancel();
        }
    }
}
