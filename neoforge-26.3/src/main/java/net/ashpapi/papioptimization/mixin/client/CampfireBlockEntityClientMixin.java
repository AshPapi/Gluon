package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.math.FastMath;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PapiOptimization Client Campfire Smoke Particle Optimizer:
 * - Distance culls cozy smoke (>48 blocks) where ~8 block high smoke is physically imperceptible.
 * - Preserves distant visibility for signal fire smoke columns on the horizon.
 * - Respects Video Settings (Particles: Decreased / Minimal).
 */
@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
@Mixin(value = CampfireBlockEntity.class, priority = 1500)
public abstract class CampfireBlockEntityClientMixin {

    @Inject(method = "particleTick", at = @At("HEAD"), cancellable = true)
    private static void papi$optimizeParticleTick(
            Level level, BlockPos pos, BlockState state, CampfireBlockEntity entity, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        double dx = (double) pos.getX() + 0.5 - player.getX();
        double dy = (double) pos.getY() + 0.5 - player.getY();
        double dz = (double) pos.getZ() + 0.5 - player.getZ();

        boolean isSignalFire = state.getOptionalValue(CampfireBlock.SIGNAL_FIRE).orElse(false);

        // 1. Distance culling for non-signal cosy smoke
        if (!isSignalFire) {
            if (!FastMath.isWithinDistance(dx, dy, dz, 48.0)) {
                ci.cancel();
                return;
            }

            if (!FastMath.isWithinDistance(dx, dy, dz, 24.0) && ((pos.getX() ^ pos.getZ() ^ (int) level.getGameTime()) & 1) == 0) {
                ci.cancel();
                return;
            }
        }

        // 2. Respect particle status setting
        ParticleStatus status = Minecraft.getInstance().options.particles().get();
        if (status == ParticleStatus.MINIMAL) {
            if (!isSignalFire || (level.getGameTime() % 5L != 0L)) {
                ci.cancel();
                return;
            }
        } else if (status == ParticleStatus.DECREASED) {
            if (((pos.hashCode() + (int) level.getGameTime()) & 1) == 0) {
                ci.cancel();
                return;
            }
        }
    }
}
