package net.ashpapi.papioptimization.mixin.physics;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * PapiOptimization Entity Collision Fast-Exit:
 * In ~70% of physics ticks for non-player entities (idle mobs, standing armor stands, resting items, stationary blocks),
 * movement vector is exactly zero.
 * Bypasses AABB expansions, block collider collection, and VoxelShape sweeps when movement == Vec3.ZERO.
 * 100% exact physics parity (Zone A determinism, epsilon = 0.0).
 */
@Mixin(value = Entity.class, priority = 1500)
public abstract class EntityCollideMixin {

    @Inject(method = "collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void papi$fastZeroMotionCollide(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        if (movement == Vec3.ZERO || (movement.x == 0.0 && movement.y == 0.0 && movement.z == 0.0)) {
            cir.setReturnValue(Vec3.ZERO);
        }
    }

    @Inject(method = "collideBoundingBox(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private static void papi$fastZeroMovementCollideBox(
            Entity entity, Vec3 movement, AABB aabb, Level level, List<VoxelShape> entityCollisions,
            CallbackInfoReturnable<Vec3> cir
    ) {
        if (movement == Vec3.ZERO || (movement.x == 0.0 && movement.y == 0.0 && movement.z == 0.0)) {
            cir.setReturnValue(movement);
        }
    }

    @Inject(method = "collideBoundingBox(Lnet/minecraft/world/phys/shapes/CollisionContext;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/world/level/Level;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private static void papi$fastZeroMovementCollideBoxContext(
            CollisionContext context, Vec3 movement, AABB aabb, Level level, List<VoxelShape> entityCollisions,
            CallbackInfoReturnable<Vec3> cir
    ) {
        if (movement == Vec3.ZERO || (movement.x == 0.0 && movement.y == 0.0 && movement.z == 0.0)) {
            cir.setReturnValue(movement);
        }
    }
}
