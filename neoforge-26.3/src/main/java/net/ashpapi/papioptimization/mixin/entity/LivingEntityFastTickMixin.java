package net.ashpapi.papioptimization.mixin.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LivingEntity.class, priority = 1500)
public abstract class LivingEntityFastTickMixin extends Entity {

    public LivingEntityFastTickMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow public abstract boolean isDeadOrDying();

    @Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
    private void papi$skipDeadOrSpectatorPush(CallbackInfo ci) {
        if (this.isSpectator() || this.isDeadOrDying()) {
            ci.cancel();
        }
    }
}
