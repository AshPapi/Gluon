package net.ashpapi.papioptimization.mixin.map;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(value = MapItemSavedData.class, priority = 1500)
public interface MapItemSavedDataAccessor {
    @Accessor("carriedByPlayers")
    Map<Player, MapItemSavedData.HoldingPlayer> getCarriedByPlayers();

    @Accessor("carriedBy")
    List<MapItemSavedData.HoldingPlayer> getCarriedBy();
}
