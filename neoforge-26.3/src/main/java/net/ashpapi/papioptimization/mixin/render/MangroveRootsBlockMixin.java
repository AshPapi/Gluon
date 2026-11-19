package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MangroveRootsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;

@OnlyIn(Dist.CLIENT)
@Mixin(value = MangroveRootsBlock.class, priority = 1500)
public abstract class MangroveRootsBlockMixin {

    @WrapMethod(method = "skipRendering")
    private boolean papi$cullAllAdjacentMangroveRoots(
            BlockState state,
            BlockState neighborState,
            Direction direction,
            Operation<Boolean> original
    ) {
        if (neighborState.is(Blocks.MANGROVE_ROOTS)) {
            if (direction.getAxis() == Direction.Axis.Y) {
                return true;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null && !mc.options.cutoutLeaves().get()) {
                return true;
            }
        }
        return original.call(state, neighborState, direction);
    }
}
