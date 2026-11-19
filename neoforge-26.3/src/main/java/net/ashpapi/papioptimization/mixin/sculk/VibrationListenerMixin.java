package net.ashpapi.papioptimization.mixin.sculk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.sculk.SculkOcclusionHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = VibrationSystem.Listener.class, priority = 1500)
public abstract class VibrationListenerMixin {

    /**
     * @author AshPapi
     * @reason Fast wool occlusion check: palette pre-check + zero-allocation DDA raycast with 100% vanilla parity.
     */
    @WrapMethod(method = "isOccluded")
    private static boolean papi$fastIsOccluded(
            Level level,
            Vec3 origin,
            Vec3 dest,
            Operation<Boolean> original
    ) {
        return SculkOcclusionHelper.isOccluded(level, origin, dest);
    }
}
