package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = Block.class, priority = 1500)
public abstract class MoreBlockCullingMixin {

    @Unique
    private static boolean papi$isVanillaFlatCarpet(Block block) {
        Class<?> cls = block.getClass();
        return cls == CarpetBlock.class || cls == WoolCarpetBlock.class;
    }

    @WrapMethod(method = "shouldRenderFace(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z")
    private static boolean papi$cullUniformLayersAndCarpets(
            BlockGetter level,
            BlockPos pos,
            BlockState state,
            BlockState adjacentState,
            Direction direction,
            Operation<Boolean> original
    ) {
        if (direction.getAxis().isHorizontal()) {
            Block selfBlock = state.getBlock();
            if (selfBlock instanceof SnowLayerBlock
                    && adjacentState.is(selfBlock)
                    && state.hasProperty(SnowLayerBlock.LAYERS)
                    && adjacentState.hasProperty(SnowLayerBlock.LAYERS)
                    && adjacentState.getValue(SnowLayerBlock.LAYERS) >= state.getValue(SnowLayerBlock.LAYERS)) {
                return false;
            }
            if (papi$isVanillaFlatCarpet(selfBlock) && papi$isVanillaFlatCarpet(adjacentState.getBlock())) {
                return false;
            }
        }
        return original.call(level, pos, state, adjacentState, direction);
    }
}
