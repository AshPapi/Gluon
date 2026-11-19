package net.ashpapi.papioptimization.mixin.physics;

import net.ashpapi.papioptimization.physics.FastBoxCollision;
import net.ashpapi.papioptimization.physics.PapiVoxelShapeExtension;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Shapes.class, priority = 1500)
public abstract class ShapesCollideMixin {

    @Inject(method = "collide", at = @At("HEAD"), cancellable = true)
    private static void papi$fastCollide(
            Direction.Axis axis,
            AABB moving,
            Iterable<VoxelShape> shapes,
            double distance,
            CallbackInfoReturnable<Double> cir
    ) {
        if (Math.abs(distance) < 1.0E-7) {
            cir.setReturnValue(0.0);
            return;
        }

        for (VoxelShape shape : shapes) {
            if (shape instanceof PapiVoxelShapeExtension ext && ext.papi$isSingleBox()) {
                AABB box = ext.papi$getSingleBox();
                if (box != null) {
                    distance = FastBoxCollision.collideSingleBox(axis, moving, box, distance);
                } else {
                    distance = shape.collide(axis, moving, distance);
                }
            } else {
                distance = shape.collide(axis, moving, distance);
            }
            if (Math.abs(distance) < 1.0E-7) {
                cir.setReturnValue(0.0);
                return;
            }
        }
        cir.setReturnValue(distance);
    }

    @Inject(method = "joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z", at = @At("HEAD"), cancellable = true)
    private static void papi$fastJoinIsNotEmpty(
            VoxelShape first,
            VoxelShape second,
            BooleanOp op,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (op == BooleanOp.AND) {
            if (first.isEmpty() || second.isEmpty()) {
                cir.setReturnValue(false);
                return;
            }
            if (first == second) {
                cir.setReturnValue(true);
                return;
            }
            if (first instanceof PapiVoxelShapeExtension ext1 && second instanceof PapiVoxelShapeExtension ext2) {
                if (ext1.papi$isSingleBox() && ext2.papi$isSingleBox()) {
                    AABB b1 = ext1.papi$getSingleBox();
                    AABB b2 = ext2.papi$getSingleBox();
                    if (b1 != null && b2 != null) {
                        cir.setReturnValue(
                                b1.minX + 1.0E-7 < b2.maxX
                                        && b1.maxX - 1.0E-7 > b2.minX
                                        && b1.minY + 1.0E-7 < b2.maxY
                                        && b1.maxY - 1.0E-7 > b2.minY
                                        && b1.minZ + 1.0E-7 < b2.maxZ
                                        && b1.maxZ - 1.0E-7 > b2.minZ
                        );
                    }
                }
            }
        }
    }

    @Inject(method = "or(Lnet/minecraft/world/phys/shapes/VoxelShape;[Lnet/minecraft/world/phys/shapes/VoxelShape;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("HEAD"), cancellable = true)
    private static void papi$fastOrArray(
            VoxelShape first,
            VoxelShape[] tail,
            CallbackInfoReturnable<VoxelShape> cir
    ) {
        if (tail == null || tail.length == 0) {
            cir.setReturnValue(first);
            return;
        }
        VoxelShape result = first;
        for (VoxelShape shape : tail) {
            result = Shapes.or(result, shape);
        }
        cir.setReturnValue(result);
    }

    @Inject(method = "faceShapeOccludes", at = @At("HEAD"), cancellable = true)
    private static void papi$fastFaceShapeOccludes(
            VoxelShape shape1, VoxelShape shape2, CallbackInfoReturnable<Boolean> cir
    ) {
        if (shape1 == Shapes.block() || shape2 == Shapes.block()) {
            cir.setReturnValue(true);
            return;
        }
        if (shape1.isEmpty() && shape2.isEmpty()) {
            cir.setReturnValue(false);
            return;
        }
    }

    @Inject(method = "mergedFaceOccludes", at = @At("HEAD"), cancellable = true)
    private static void papi$fastMergedFaceOccludes(
            VoxelShape shape1, VoxelShape shape2, Direction direction, CallbackInfoReturnable<Boolean> cir
    ) {
        if (shape1 == Shapes.block() || shape2 == Shapes.block()) {
            cir.setReturnValue(true);
            return;
        }
        if (shape1.isEmpty() && shape2.isEmpty()) {
            cir.setReturnValue(false);
        }
    }
}
