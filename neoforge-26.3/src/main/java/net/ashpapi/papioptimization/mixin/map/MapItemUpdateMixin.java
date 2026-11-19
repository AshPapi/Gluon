package net.ashpapi.papioptimization.mixin.map;

import net.ashpapi.papioptimization.map.FastMapColorScanner;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MapItem.class, priority = 1500)
public abstract class MapItemUpdateMixin {

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void papi$fastMapUpdate(Level level, Entity player, MapItemSavedData data, CallbackInfo ci) {
        FastMapColorScanner.updateMap(level, player, data);
        ci.cancel();
    }
}
