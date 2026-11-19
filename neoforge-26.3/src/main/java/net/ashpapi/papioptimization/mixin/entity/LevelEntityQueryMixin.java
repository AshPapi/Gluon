package net.ashpapi.papioptimization.mixin.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Optimizes Level.getEntities hot path queries:
 * 1. Fast early-exit for inverted or NaN bounding boxes without allocating lambdas or spatial search trees.
 * 2. Returns a mutable ArrayList to guarantee 100% vanilla compatibility (preventing UnsupportedOperationException).
 * 3. Preserves zero-thickness point and flat plane queries (min == max) which legitimately intersect 3D entities.
 */
@Mixin(value = Level.class, priority = 1500)
public abstract class LevelEntityQueryMixin {

    @Inject(
        method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void papi$fastEmptyBoxCheck(
            Entity except,
            AABB box,
            Predicate<? super Entity> predicate,
            CallbackInfoReturnable<List<Entity>> cir
    ) {
        if (box == null || box.getXsize() < 0.0 || box.getYsize() < 0.0 || box.getZsize() < 0.0 || box.hasNaN()) {
            cir.setReturnValue(new ArrayList<>(0));
        }
    }

    @Inject(
        method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At("HEAD"),
        cancellable = true
    )
    private <T extends Entity> void papi$fastTypeEmptyBoxCheck(
            EntityTypeTest<Entity, T> test,
            AABB box,
            Predicate<? super T> predicate,
            CallbackInfoReturnable<List<T>> cir
    ) {
        if (box == null || box.getXsize() < 0.0 || box.getYsize() < 0.0 || box.getZsize() < 0.0 || box.hasNaN()) {
            cir.setReturnValue(new ArrayList<>(0));
        }
    }
}
