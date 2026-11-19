package net.ashpapi.papioptimization.mixin.entity;

import net.ashpapi.papioptimization.core.ArmorStandOptimizer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class, priority = 1500)
public abstract class LivingEntityArmorStandTickMixin extends Entity {

    protected LivingEntityArmorStandTickMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * Skips heavy LivingEntity ticking (super.tick(), baseTick(), equipment checks, aiStep,
     * travel, fluid checks, block effects) for sleeping or marker ArmorStands.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void papi$throttleArmorStandTick(CallbackInfo ci) {
        if ((Object) this instanceof ArmorStand stand && ArmorStandOptimizer.shouldSkipTick(stand)) {
            ci.cancel();
        }
    }
}
