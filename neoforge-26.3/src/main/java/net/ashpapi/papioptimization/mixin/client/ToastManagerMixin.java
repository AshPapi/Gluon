package net.ashpapi.papioptimization.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Deque;
import java.util.List;
import java.util.Set;

@Mixin(value = ToastManager.class, priority = 1500)
public abstract class ToastManagerMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private List<?> visibleToasts;
    @Shadow @Final private Deque<Toast> queued;
    @Shadow @Final private Set<SoundEvent> playedToastSounds;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void papi$skipIdleToastUpdate(CallbackInfo ci) {
        if (this.visibleToasts.isEmpty() && this.queued.isEmpty() && !this.minecraft.options.musicToast().get().renderToast()) {
            if (!this.playedToastSounds.isEmpty()) {
                this.playedToastSounds.clear();
            }
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void papi$skipIdleToastRender(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (this.visibleToasts.isEmpty() && !this.minecraft.options.musicToast().get().renderToast()) {
            ci.cancel();
        }
    }
}
