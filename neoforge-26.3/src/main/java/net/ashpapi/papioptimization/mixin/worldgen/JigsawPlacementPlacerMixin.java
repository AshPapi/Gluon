package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.worldgen.FastJigsawSpace;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement$Placer", priority = 1500)
public abstract class JigsawPlacementPlacerMixin {

    @WrapOperation(
        method = "tryPlacingChildren",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/Shapes;joinIsNotEmpty(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Z"
        )
    )
    private boolean papi$fastJoinIsNotEmpty(
            VoxelShape first,
            VoxelShape second,
            BooleanOp op,
            Operation<Boolean> original
    ) {
        if (op != BooleanOp.ONLY_SECOND || !(first instanceof FastJigsawSpace fastSpace)) {
            return original.call(first, second, op);
        }
        return fastSpace.isCollidingOrOutside(FastJigsawSpace.toPieceBoundingBox(second.bounds()));
    }

    @WrapOperation(
        method = "tryPlacingChildren",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/Shapes;joinUnoptimized(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
        )
    )
    private VoxelShape papi$fastJoinUnoptimized(
            VoxelShape first,
            VoxelShape second,
            BooleanOp op,
            Operation<VoxelShape> original
    ) {
        if (op != BooleanOp.ONLY_FIRST || !(first instanceof FastJigsawSpace fastSpace)) {
            return original.call(first, second, op);
        }
        fastSpace.addPiece(FastJigsawSpace.toPieceBoundingBox(second.bounds()));
        return fastSpace;
    }
}
