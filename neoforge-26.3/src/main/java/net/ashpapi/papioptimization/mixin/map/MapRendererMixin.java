package net.ashpapi.papioptimization.mixin.map;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.client.MapDecorationCache;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MapRenderer.class, priority = 1500)
public abstract class MapRendererMixin {
    @Shadow @Final private TextureAtlas decorationSprites;

    @WrapOperation(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MapRenderer;extractDecorationRenderState(Lnet/minecraft/world/level/saveddata/maps/MapDecoration;)Lnet/minecraft/client/renderer/state/MapRenderState$MapDecorationRenderState;"
        )
    )
    private MapRenderState.MapDecorationRenderState papi$cachedDecorationRenderState(
            MapRenderer renderer, MapDecoration decoration, Operation<MapRenderState.MapDecorationRenderState> original) {
        return MapDecorationCache.getOrCreate(decoration, this.decorationSprites);
    }
}
