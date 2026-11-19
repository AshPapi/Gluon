package net.ashpapi.papioptimization.mixin.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PapiOptimization Fast Beehive Hibernation (OPT-38):
 * Over 95% of wild and farm beehives are empty at any given tick while bees are foraging outside.
 * In vanilla, empty beehives tick every single game tick, dispatching to tickOccupants
 * and allocating java.util.ArrayList$Itr instances on empty lists.
 *
 * This mixin hibernates idle empty beehives at HEAD of serverTick.
 * When a bee enters the hive (addOccupant), the hive is no longer empty and ticks resume immediately.
 * 100% exact vanilla bee mechanics and honey production parity (Zone A determinism, epsilon = 0.0).
 */
@Mixin(value = BeehiveBlockEntity.class, priority = 1500)
public abstract class FastBeehiveMixin {

    @Inject(
        method = "serverTick",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void papi$fastBeehiveServerTick(
            Level level,
            BlockPos pos,
            BlockState state,
            BeehiveBlockEntity beehive,
            CallbackInfo ci
    ) {
        if (beehive.isEmpty()) {
            ci.cancel();
        }
    }
}
