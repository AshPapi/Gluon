package net.ashpapi.papioptimization.mixin.piston;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = PistonBaseBlock.class, priority = 1500)
public abstract class PistonBaseBlockMixin {
    @Unique
    private static final Direction[] PAPI$DIRECTIONS = Direction.values();

    /**
     * @author AshPapi
     * @reason Avoid allocating Direction.values() arrays on every redstone signal check.
     */
    @WrapMethod(method = "getNeighborSignal")
    private boolean papi$fastGetNeighborSignal(
            SignalGetter level,
            BlockPos pos,
            Direction pushDirection,
            Operation<Boolean> original
    ) {
        for (Direction direction : PAPI$DIRECTIONS) {
            if (direction != pushDirection && level.hasSignal(pos.relative(direction), direction)) {
                return true;
            }
        }
        if (level.hasSignal(pos, Direction.DOWN)) {
            return true;
        }
        BlockPos above = pos.above();
        for (Direction direction : PAPI$DIRECTIONS) {
            if (direction != Direction.DOWN && level.hasSignal(above.relative(direction), direction)) {
                return true;
            }
        }
        return false;
    }
}
