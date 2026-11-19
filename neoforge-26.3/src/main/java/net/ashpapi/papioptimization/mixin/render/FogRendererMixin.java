package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.ref.WeakReference;
import net.ashpapi.papioptimization.client.PapiClientCaches;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = FogRenderer.class, priority = 1500)
public abstract class FogRendererMixin {

    @Shadow
    protected abstract FogType getFogType(Camera camera);

    @Unique
    private WeakReference<ClientLevel> papi$lastLevel = new WeakReference<>(null);

    @Unique
    private int papi$lastEpoch = -1;

    @Unique
    private long papi$lastGameTime = Long.MIN_VALUE;

    @Unique
    private int papi$lastSubTick = -1;

    @Unique
    private FogType papi$lastFogType;

    @Unique
    private int papi$lastCamX = Integer.MIN_VALUE;

    @Unique
    private int papi$lastCamY = Integer.MIN_VALUE;

    @Unique
    private int papi$lastCamZ = Integer.MIN_VALUE;

    @Unique
    private int papi$lastRenderDist = Integer.MIN_VALUE;

    @Unique
    private int papi$lastDarkenBits;

    @Unique
    private int papi$lastRainBits;

    @Unique
    private int papi$lastLookX;

    @Unique
    private int papi$lastLookZ;

    @Unique
    private float papi$cachedR;

    @Unique
    private float papi$cachedG;

    @Unique
    private float papi$cachedB;

    @Unique
    private float papi$cachedA;

    @Unique
    private boolean papi$cacheValid;

    @WrapMethod(method = "computeFogColor")
    private void papi$cacheFogColor(
            Camera camera,
            float partialTick,
            ClientLevel level,
            int renderDistanceChunks,
            float darkenWorldAmount,
            Vector4f output,
            Operation<Void> original
    ) {
        if (level == null || camera == null || level.isThundering() || camera.isPanoramicMode()) {
            this.papi$cacheValid = false;
            original.call(camera, partialTick, level, renderDistanceChunks, darkenWorldAmount, output);
            return;
        }
        FogType fogType = this.getFogType(camera);
        if (fogType == FogType.WATER || fogType == FogType.LAVA || fogType == FogType.POWDER_SNOW) {
            this.papi$cacheValid = false;
            original.call(camera, partialTick, level, renderDistanceChunks, darkenWorldAmount, output);
            return;
        }
        Entity entity = camera.entity();
        if (entity instanceof LocalPlayer lp && lp.getWaterVision() > 0.0F && lp.getWaterVision() < 1.0F) {
            this.papi$cacheValid = false;
            original.call(camera, partialTick, level, renderDistanceChunks, darkenWorldAmount, output);
            return;
        }
        if (entity instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS)
                || living.hasEffect(MobEffects.DARKNESS)
                || living.hasEffect(MobEffects.NIGHT_VISION))) {
            this.papi$cacheValid = false;
            original.call(camera, partialTick, level, renderDistanceChunks, darkenWorldAmount, output);
            return;
        }

        int epoch = PapiClientCaches.epoch();
        long gameTime = level.getGameTime();
        int subTick = (int) (partialTick * 4.0F);
        Vec3 camPos = camera.position();
        int camX = (int) Math.floor(camPos.x * 4.0);
        int camY = (int) Math.floor(camPos.y * 4.0);
        int camZ = (int) Math.floor(camPos.z * 4.0);
        int darkenBits = Float.floatToRawIntBits(darkenWorldAmount);
        int rainBits = Float.floatToRawIntBits(level.getRainLevel(partialTick));
        Vector3fc fwd = camera.forwardVector();
        int lookX = Math.round(fwd.x() * 16.0F);
        int lookZ = Math.round(fwd.z() * 16.0F);

        if (this.papi$cacheValid
                && this.papi$lastEpoch == epoch
                && this.papi$lastLevel.get() == level
                && this.papi$lastGameTime == gameTime
                && this.papi$lastSubTick == subTick
                && this.papi$lastFogType == fogType
                && this.papi$lastCamX == camX
                && this.papi$lastCamY == camY
                && this.papi$lastCamZ == camZ
                && this.papi$lastRenderDist == renderDistanceChunks
                && this.papi$lastDarkenBits == darkenBits
                && this.papi$lastRainBits == rainBits
                && this.papi$lastLookX == lookX
                && this.papi$lastLookZ == lookZ) {
            output.set(this.papi$cachedR, this.papi$cachedG, this.papi$cachedB, this.papi$cachedA);
            return;
        }

        original.call(camera, partialTick, level, renderDistanceChunks, darkenWorldAmount, output);

        this.papi$lastEpoch = epoch;
        this.papi$lastLevel = new WeakReference<>(level);
        this.papi$lastGameTime = gameTime;
        this.papi$lastSubTick = subTick;
        this.papi$lastFogType = fogType;
        this.papi$lastCamX = camX;
        this.papi$lastCamY = camY;
        this.papi$lastCamZ = camZ;
        this.papi$lastRenderDist = renderDistanceChunks;
        this.papi$lastDarkenBits = darkenBits;
        this.papi$lastRainBits = rainBits;
        this.papi$lastLookX = lookX;
        this.papi$lastLookZ = lookZ;
        this.papi$cachedR = output.x;
        this.papi$cachedG = output.y;
        this.papi$cachedB = output.z;
        this.papi$cachedA = output.w;
        this.papi$cacheValid = true;
    }
}
