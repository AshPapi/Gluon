package net.ashpapi.papioptimization.mixin.piston;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = PistonStructureResolver.class, priority = 1500)
public abstract class PistonStructureResolverMixin {
    @Shadow @Final private List<BlockPos> toPush;

    /**
     * @author AshPapi
     * @reason Eliminate 3 ArrayList allocations on every collision reorder by using in-place list rotation.
     */
    @WrapMethod(method = "reorderListAtCollision")
    private void papi$fastReorderListAtCollision(int blocksAdded, int collisionPos, Operation<Void> original) {
        Collections.rotate(this.toPush.subList(collisionPos, this.toPush.size()), blocksAdded);
    }
}
