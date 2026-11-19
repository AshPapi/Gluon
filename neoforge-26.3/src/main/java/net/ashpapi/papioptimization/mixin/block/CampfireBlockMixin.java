package net.ashpapi.papioptimization.mixin.block;

import net.ashpapi.papioptimization.core.BlockPosPool;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CampfireBlock.class, priority = 1500)
public abstract class CampfireBlockMixin {

    @Unique
    private static final VoxelShape PAPI$SMOKE_POST = Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);

    @Inject(method = "isSmokeyPos", at = @At("HEAD"), cancellable = true)
    private static void papi$fastIsSmokeyPos(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        int mark = BlockPosPool.mark();
        try {
            BlockPos.MutableBlockPos checkPos = BlockPosPool.get();
            for (int i = 1; i <= 5; ++i) {
                checkPos.set(pos.getX(), pos.getY() - i, pos.getZ());
                BlockState state = level.getBlockState(checkPos);

                if (CampfireBlock.isLitCampfire(state)) {
                    cir.setReturnValue(true);
                    return;
                }

                if (state.isAir()) {
                    continue;
                }

                if (state.isCollisionShapeFullBlock(level, checkPos)) {
                    checkPos.setY(checkPos.getY() - 1);
                    cir.setReturnValue(CampfireBlock.isLitCampfire(level.getBlockState(checkPos)));
                    return;
                }

                VoxelShape shape = state.getCollisionShape(level, checkPos, CollisionContext.empty());
                if (Shapes.joinIsNotEmpty(PAPI$SMOKE_POST, shape, BooleanOp.AND)) {
                    checkPos.setY(checkPos.getY() - 1);
                    cir.setReturnValue(CampfireBlock.isLitCampfire(level.getBlockState(checkPos)));
                    return;
                }
            }
            cir.setReturnValue(false);
        } finally {
            BlockPosPool.reset(mark);
        }
    }
}
