package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
@Mixin(value = LeavesBlock.class, priority = 1500)
public abstract class LeavesBlockMixin extends Block {

    @Shadow
    private static volatile boolean cutoutLeaves;

    public LeavesBlockMixin(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @WrapMethod(method = "skipRendering")
    private boolean papi$skipInnerLeafFacesWhenOpaque(
            BlockState state,
            BlockState adjacentState,
            Direction direction,
            Operation<Boolean> original
    ) {
        if (!cutoutLeaves && adjacentState.getBlock() instanceof LeavesBlock) {
            return true;
        }
        return original.call(state, adjacentState, direction);
    }
}
