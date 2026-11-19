package net.ashpapi.papioptimization.mixin.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = FireworkRocketEntity.class, priority = 1500)
public abstract class FireworkRocketFastMixin {

    @Shadow private @Nullable LivingEntity attachedToEntity;
    @Shadow private native List<FireworkExplosion> getExplosions();

    @Inject(method = "dealExplosionDamage", at = @At("HEAD"), cancellable = true)
    private void papi$fastExplosionDamage(ServerLevel level, CallbackInfo ci) {
        FireworkRocketEntity rocket = (FireworkRocketEntity) (Object) this;
        List<FireworkExplosion> explosions = this.getExplosions();
        if (explosions.isEmpty()) {
            ci.cancel();
            return;
        }

        float damageAmount = 5.0f + (float) (explosions.size() * 2);

        if (this.attachedToEntity != null) {
            this.attachedToEntity.hurtServer(
                    level,
                    rocket.damageSources().fireworks(rocket, rocket.getOwner()),
                    damageAmount
            );
        }

        Vec3 rocketPos = rocket.position();
        BlockPos rocketBlock = BlockPos.containing(rocketPos);
        boolean rocketInEmptyBlock = level.getBlockState(rocketBlock).getCollisionShape(level, rocketBlock).isEmpty();

        for (LivingEntity target : rocket.level().getEntitiesOfClass(LivingEntity.class, rocket.getBoundingBox().inflate(5.0))) {
            if (target == this.attachedToEntity) continue;

            double distSqr = rocket.distanceToSqr(target);
            if (distSqr > 25.0) continue;

            double dist = Math.sqrt(distSqr);
            boolean canSee = false;

            BlockPos targetBlock = target.blockPosition();
            if (rocketInEmptyBlock && rocketBlock.equals(targetBlock)) {
                canSee = true;
            } else {
                for (int testStep = 0; testStep < 2; ++testStep) {
                    Vec3 to = new Vec3(target.getX(), target.getY(0.5 * (double) testStep), target.getZ());
                    BlockHitResult clip = level.clip(new ClipContext(rocketPos, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rocket));
                    if (clip.getType() == HitResult.Type.MISS) {
                        canSee = true;
                        break;
                    }
                }
            }

            if (canSee) {
                float damage = damageAmount * (float) Math.sqrt((5.0 - dist) / 5.0);
                target.hurtServer(level, rocket.damageSources().fireworks(rocket, rocket.getOwner()), damage);
            }
        }

        ci.cancel();
    }
}
