package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.lang.ref.WeakReference;
import net.ashpapi.papioptimization.client.PapiClientCaches;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = SkyRenderer.class, priority = 1500)
public abstract class SkyRendererMixin {

    @Unique
    private WeakReference<ClientLevel> papi$lastLevel = new WeakReference<>(null);

    @Unique
    private int papi$lastEpoch = -1;

    @Unique
    private long papi$lastGameTime = Long.MIN_VALUE;

    @Unique
    private int papi$lastQuartX = Integer.MIN_VALUE;

    @Unique
    private int papi$lastQuartY = Integer.MIN_VALUE;

    @Unique
    private int papi$lastQuartZ = Integer.MIN_VALUE;

    @Unique
    private int papi$lastRainBits;

    @Unique
    private int papi$lastSubTick = Integer.MIN_VALUE;

    @Unique
    private boolean papi$frameCacheHit;

    @Unique
    private Object papi$cachedSkyColor;

    @Unique
    private Object papi$cachedSunriseColor;

    @Unique
    private Object papi$cachedStarBrightness;

    @Unique
    private Object papi$cachedMoonPhase;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void papi$prepareSkyColorCache(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        if (level == null || camera == null || level.isThundering()) {
            this.papi$frameCacheHit = false;
            this.papi$cachedSkyColor = null;
            this.papi$cachedSunriseColor = null;
            this.papi$cachedStarBrightness = null;
            this.papi$cachedMoonPhase = null;
            return;
        }
        int epoch = PapiClientCaches.epoch();
        long gameTime = level.getGameTime();
        BlockPos pos = camera.blockPosition();
        int qx = pos.getX() >> 2;
        int qy = pos.getY() >> 2;
        int qz = pos.getZ() >> 2;
        int rainBits = Float.floatToRawIntBits(level.getRainLevel(1.0F));
        int subTick = (int) (partialTick * 8.0F);

        if (this.papi$lastEpoch == epoch
                && this.papi$lastLevel.get() == level
                && this.papi$lastGameTime == gameTime
                && this.papi$lastSubTick == subTick
                && this.papi$lastQuartX == qx
                && this.papi$lastQuartY == qy
                && this.papi$lastQuartZ == qz
                && this.papi$lastRainBits == rainBits) {
            this.papi$frameCacheHit = true;
            return;
        }

        this.papi$lastEpoch = epoch;
        this.papi$lastLevel = new WeakReference<>(level);
        this.papi$lastGameTime = gameTime;
        this.papi$lastSubTick = subTick;
        this.papi$lastQuartX = qx;
        this.papi$lastQuartY = qy;
        this.papi$lastQuartZ = qz;
        this.papi$lastRainBits = rainBits;
        this.papi$frameCacheHit = false;
        this.papi$cachedSkyColor = null;
        this.papi$cachedSunriseColor = null;
        this.papi$cachedStarBrightness = null;
        this.papi$cachedMoonPhase = null;
    }

    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/attribute/EnvironmentAttributeProbe;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;F)Ljava/lang/Object;"
        )
    )
    private Object papi$cachedSkyAttributes(
            EnvironmentAttributeProbe probe,
            EnvironmentAttribute<?> attribute,
            float partialTick,
            Operation<Object> original
    ) {
        if (attribute == EnvironmentAttributes.SKY_COLOR) {
            if (this.papi$frameCacheHit && this.papi$cachedSkyColor != null) {
                return this.papi$cachedSkyColor;
            }
            Object val = original.call(probe, attribute, partialTick);
            this.papi$cachedSkyColor = val;
            return val;
        }
        if (attribute == EnvironmentAttributes.SUNRISE_SUNSET_COLOR) {
            if (this.papi$frameCacheHit && this.papi$cachedSunriseColor != null) {
                return this.papi$cachedSunriseColor;
            }
            Object val = original.call(probe, attribute, partialTick);
            this.papi$cachedSunriseColor = val;
            return val;
        }
        if (attribute == EnvironmentAttributes.STAR_BRIGHTNESS) {
            if (this.papi$frameCacheHit && this.papi$cachedStarBrightness != null) {
                return this.papi$cachedStarBrightness;
            }
            Object val = original.call(probe, attribute, partialTick);
            this.papi$cachedStarBrightness = val;
            return val;
        }
        if (attribute == EnvironmentAttributes.MOON_PHASE) {
            if (this.papi$frameCacheHit && this.papi$cachedMoonPhase != null) {
                return this.papi$cachedMoonPhase;
            }
            Object val = original.call(probe, attribute, partialTick);
            this.papi$cachedMoonPhase = val;
            return val;
        }
        return original.call(probe, attribute, partialTick);
    }
}
