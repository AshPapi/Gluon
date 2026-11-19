package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.worldgen.FastJigsawSpace;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = JigsawPlacement.class, priority = 1500)
public abstract class JigsawPlacementMixin {

    @WrapOperation(
        method = "lambda$addPieces$2",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/Shapes;join(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
        )
    )
    private static VoxelShape papi$wrapInitialFreeSpace(
            VoxelShape outerShape,
            VoxelShape centerPieceShape,
            BooleanOp op,
            Operation<VoxelShape> original
    ) {
        FastJigsawSpace space = new FastJigsawSpace(outerShape.bounds());
        space.addPiece(FastJigsawSpace.toPieceBoundingBox(centerPieceShape.bounds()));
        return space;
    }
}
