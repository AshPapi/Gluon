package net.ashpapi.papioptimization.mixin.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(value = Entity.class, priority = 1500)
public abstract class EntityFastFluidMixin {

    @Shadow @Final protected EntityFluidInteraction fluidInteraction;
    @Shadow protected boolean wasTouchingWater;
    @Shadow protected boolean firstTick;

    @Shadow public abstract void resetFallDistance();
    @Shadow protected abstract void doWaterSplashEffect();

    @Unique
    private Predicate<FluidType> papi$pushedByFluidPredicate;

    @Inject(method = "collidedWithFluid", at = @At("HEAD"), cancellable = true)
    private void papi$fastSkipEmptyFluidCollision(FluidState fluidState, BlockPos pos, Vec3 from, Vec3 to, CallbackInfoReturnable<Boolean> cir) {
        if (fluidState.isEmpty()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "updateFluidInteraction", at = @At("HEAD"), cancellable = true)
    private void papi$fastUpdateFluidInteraction(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        Predicate<FluidType> pred = this.papi$pushedByFluidPredicate;
        if (pred == null) {
            pred = self::isPushedByFluid;
            this.papi$pushedByFluidPredicate = pred;
        }
        boolean inAnyFluid = this.fluidInteraction.update(self, pred);
        if (!inAnyFluid) {
            this.wasTouchingWater = false;
            cir.setReturnValue(false);
            return;
        }
        boolean inWater = this.fluidInteraction.isInFluidMatching(self, (entity, type, d) -> type.getIsWaterLike());
        if (inWater) {
            this.resetFallDistance();
            if (!this.wasTouchingWater && !this.firstTick) {
                this.doWaterSplashEffect();
            }
        }
        this.wasTouchingWater = inWater;
        this.fluidInteraction.applyCurrentTo(self);
        cir.setReturnValue(true);
    }
}
