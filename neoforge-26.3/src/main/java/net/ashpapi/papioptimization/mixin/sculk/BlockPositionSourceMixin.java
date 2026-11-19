package net.ashpapi.papioptimization.mixin.sculk;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockPositionSource.class, priority = 1500)
public abstract class BlockPositionSourceMixin {
    @Shadow
    public abstract BlockPos pos();

    @Unique
    private Optional<Vec3> papi$cachedOptional;

    @Inject(method = "getPosition", at = @At("HEAD"), cancellable = true)
    private void papi$getCachedPosition(Level level, CallbackInfoReturnable<Optional<Vec3>> cir) {
        if (this.papi$cachedOptional == null) {
            this.papi$cachedOptional = Optional.of(Vec3.atCenterOf(this.pos()));
        }
        cir.setReturnValue(this.papi$cachedOptional);
    }
}
