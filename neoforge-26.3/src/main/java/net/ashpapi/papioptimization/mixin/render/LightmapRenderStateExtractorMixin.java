package net.ashpapi.papioptimization.mixin.render;

import java.lang.ref.WeakReference;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LightmapRenderStateExtractor.class, priority = 1500)
public abstract class LightmapRenderStateExtractorMixin {
    @Shadow private boolean needsUpdate;
    @Shadow private float blockLightFlicker;
    @Shadow @Final private RandomSource randomSource;
    @Shadow @Final private Minecraft minecraft;

    @Unique private float papi$prevFlicker = 0.0f;
    @Unique private int papi$tickCounter = 0;
    @Unique private double papi$prevGamma = Double.NaN;
    @Unique private double papi$prevDarknessScale = Double.NaN;
    @Unique private float papi$prevWaterVision = Float.NaN;
    @Unique private boolean papi$prevBossFog = false;
    @Unique private int papi$prevQuartX = Integer.MIN_VALUE;
    @Unique private int papi$prevQuartY = Integer.MIN_VALUE;
    @Unique private int papi$prevQuartZ = Integer.MIN_VALUE;
    @Unique private WeakReference<ClientLevel> papi$prevLevel = new WeakReference<>(null);
    @Unique private int papi$prevSkyDarken = Integer.MIN_VALUE;
    @Unique private float papi$prevSkyLightFactor = Float.NaN;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void papi$differentialLightmapTick(CallbackInfo ci) {
        ci.cancel();

        float delta = (this.randomSource.nextFloat() - this.randomSource.nextFloat())
                * this.randomSource.nextFloat()
                * this.randomSource.nextFloat()
                * 0.1F;
        this.blockLightFlicker += delta;
        this.blockLightFlicker *= 0.9F;

        this.papi$tickCounter++;
        float flickerDiff = Math.abs(this.blockLightFlicker - this.papi$prevFlicker);
        boolean dirty = flickerDiff > 0.015F || this.papi$tickCounter >= 10;

        LocalPlayer player = this.minecraft.player;
        if (player != null) {
            if (player.hasEffect(MobEffects.NIGHT_VISION)
                    || player.hasEffect(MobEffects.DARKNESS)
                    || player.hasEffect(MobEffects.CONDUIT_POWER)) {
                dirty = true;
            }
            float waterVision = player.getWaterVision();
            if (waterVision != this.papi$prevWaterVision) {
                this.papi$prevWaterVision = waterVision;
                dirty = true;
            }
        }

        ClientLevel level = this.minecraft.level;
        if (level != this.papi$prevLevel.get()) {
            this.papi$prevLevel = new WeakReference<>(level);
            this.papi$prevSkyDarken = Integer.MIN_VALUE;
            this.papi$prevSkyLightFactor = Float.NaN;
            dirty = true;
        }
        if (level != null) {
            int skyDarken = level.getSkyDarken();
            if (skyDarken != this.papi$prevSkyDarken) {
                this.papi$prevSkyDarken = skyDarken;
                dirty = true;
            }
            if (level.isThundering()) {
                dirty = true;
            }
            EndFlashState endFlash = level.endFlashState();
            if (endFlash != null && endFlash.getIntensity(1.0F) > 0.0F) {
                dirty = true;
            }
        }

        BossHealthOverlay bossOverlay = this.minecraft.gui.hud.getBossOverlay();
        boolean bossFog = bossOverlay != null && bossOverlay.shouldCreateWorldFog();
        if (bossFog != this.papi$prevBossFog) {
            this.papi$prevBossFog = bossFog;
            dirty = true;
        }

        double gamma = this.minecraft.options.gamma().get();
        if (gamma != this.papi$prevGamma) {
            this.papi$prevGamma = gamma;
            dirty = true;
        }

        double darknessScale = this.minecraft.options.darknessEffectScale().get();
        if (darknessScale != this.papi$prevDarknessScale) {
            this.papi$prevDarknessScale = darknessScale;
            dirty = true;
        }

        Camera camera = this.minecraft.gameRenderer.mainCamera();
        if (camera != null) {
            EnvironmentAttributeProbe probe = camera.attributeProbe();
            if (probe != null) {
                float skyFactor = probe.getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, 1.0F);
                if (Float.isNaN(this.papi$prevSkyLightFactor) || Math.abs(skyFactor - this.papi$prevSkyLightFactor) > 1.0E-4F) {
                    this.papi$prevSkyLightFactor = skyFactor;
                    dirty = true;
                }
            }
            BlockPos camBlock = camera.blockPosition();
            int qx = camBlock.getX() >> 2;
            int qy = camBlock.getY() >> 2;
            int qz = camBlock.getZ() >> 2;
            if (qx != this.papi$prevQuartX || qy != this.papi$prevQuartY || qz != this.papi$prevQuartZ) {
                this.papi$prevQuartX = qx;
                this.papi$prevQuartY = qy;
                this.papi$prevQuartZ = qz;
                dirty = true;
            }
        }

        if (dirty) {
            this.needsUpdate = true;
            this.papi$prevFlicker = this.blockLightFlicker;
            this.papi$tickCounter = 0;
        }
    }
}
