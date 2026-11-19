package net.ashpapi.papioptimization.mixin.render;

import java.nio.ByteBuffer;
import net.minecraft.client.renderer.CloudRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = CloudRenderer.class, priority = 1500)
public abstract class CloudRendererMixin {

    @Inject(
        method = "buildExtrudedCell",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/Direction;values()[Lnet/minecraft/core/Direction;"
        ),
        cancellable = true
    )
    private void papi$skipInvisibleInsideCloudFaces(
            @Coerce Enum<?> relativeCameraPos,
            ByteBuffer buffer,
            int cellX,
            int cellZ,
            long cellData,
            CallbackInfo ci
    ) {
        if (relativeCameraPos.ordinal() != 1) {
            ci.cancel();
        }
    }
}
