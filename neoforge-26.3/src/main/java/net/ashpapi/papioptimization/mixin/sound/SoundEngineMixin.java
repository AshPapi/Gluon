package net.ashpapi.papioptimization.mixin.sound;

import com.mojang.blaze3d.audio.Listener;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SoundEngine.class, priority = 1500)
public abstract class SoundEngineMixin {

    @Shadow @Final private Listener listener;

    @Inject(
            method = "play",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/sounds/ChannelAccess;createHandle(Lcom/mojang/blaze3d/audio/Library$Pool;)Ljava/util/concurrent/CompletableFuture;"
            ),
            cancellable = true
    )
    private void papi$cullInaudibleDistantSound(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (!instance.isRelative()
                && instance.getAttenuation() == SoundInstance.Attenuation.LINEAR
                && !instance.isLooping()
                && !instance.canStartSilent()
                && !(instance instanceof TickableSoundInstance)) {
            SoundSource source = instance.getSource();
            if (source != SoundSource.MUSIC && source != SoundSource.RECORDS && source != SoundSource.VOICE) {
                Sound sound = instance.getSound();
                if (sound != null) {
                    float maxDist = Math.max(64.0f, sound.getAttenuationDistance(instance.getVolume()) * 4.0f);
                    if (maxDist > 0.0f) {
                        Vec3 listenerPos = this.listener.getTransform().position();
                        double dx = instance.getX() - listenerPos.x;
                        double dy = instance.getY() - listenerPos.y;
                        double dz = instance.getZ() - listenerPos.z;
                        if (dx * dx + dy * dy + dz * dz >= (double) (maxDist * maxDist)) {
                            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
                        }
                    }
                }
            }
        }
    }
}
