package net.ashpapi.papioptimization.mixin.map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapFrame;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Mixin(value = MapItemSavedData.class, priority = 1500)
public abstract class MapItemSavedDataMixin {
    @Shadow @Final private Map<String, MapFrame> frameMarkers;
    @Shadow @Final private Map<String, MapDecoration> decorations;
    @Shadow @Final private Map<Player, MapItemSavedData.HoldingPlayer> carriedByPlayers;
    @Shadow @Final private List<MapItemSavedData.HoldingPlayer> carriedBy;
    @Shadow private boolean trackingPosition;

    @Shadow
    public abstract void removeDecoration(String id);

    @Shadow
    private static Predicate<ItemStack> mapMatcher(ItemStack stack) {
        throw new AssertionError();
    }

    /**
     * Fast path for framed maps in ItemFrame:
     * When placed in a frame, the frame marker is completely static.
     * Re-adding decorations, re-allocating MapFrame objects, and iterating all carriedBy players
     * every 10 ticks for every frame on a map wall causes severe CPU and GC churn.
     */
    @Inject(method = "tickCarriedBy", at = @At("HEAD"), cancellable = true)
    private void papi$fastTickCarriedBy(Player tickingPlayer, ItemStack itemStack, @Nullable ItemFrame placedInFrame, CallbackInfo ci) {
        if (placedInFrame == null) {
            return; // Use default handling for player-carried maps
        }

        MapItemSavedData self = (MapItemSavedData) (Object) this;

        // 1. Ensure ticking player is registered to receive update packets
        if (!this.carriedByPlayers.containsKey(tickingPlayer)) {
            self.getHoldingPlayer(tickingPlayer);
        }

        String tickingName = tickingPlayer.getPlainTextName();
        if (this.decorations.containsKey(tickingName) && !tickingPlayer.getInventory().contains(mapMatcher(itemStack))) {
            this.removeDecoration(tickingName);
        }

        if ((tickingPlayer.level().getGameTime() & 31L) == 0L) {
            for (int i = this.carriedBy.size() - 1; i >= 0; i--) {
                MapItemSavedData.HoldingPlayer hp = this.carriedBy.get(i);
                if (hp.player.isRemoved()) {
                    this.carriedBy.remove(i);
                    this.carriedByPlayers.remove(hp.player);
                    this.removeDecoration(hp.player.getPlainTextName());
                }
            }
        }

        // 2. Fast-path frame marker registration (only once unless frame changed)
        if (this.trackingPosition) {
            BlockPos pos = placedInFrame.getPos();
            String frameId = MapFrame.frameId(pos);
            MapFrame existingFrame = this.frameMarkers.get(frameId);
            int rot = placedInFrame.getDirection().get2DDataValue() * 90;

            if (existingFrame == null || existingFrame.entityId() != placedInFrame.getId() || existingFrame.rotation() != rot) {
                if (existingFrame != null && existingFrame.entityId() != placedInFrame.getId()) {
                    this.removeDecoration("frame-" + existingFrame.entityId());
                }
                MapFrame mapFrame = new MapFrame(pos, rot, placedInFrame.getId());
                self.addDecoration(MapDecorationTypes.FRAME, (LevelAccessor) tickingPlayer.level(), "frame-" + placedInFrame.getId(), pos.getX(), pos.getZ(), rot, null);
                this.frameMarkers.put(frameId, mapFrame);
                self.setDirty();
            }
        }

        // 3. Static decorations (only if missing)
        MapDecorations staticDecorations = itemStack.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);
        if (!staticDecorations.decorations().isEmpty() && !this.decorations.keySet().containsAll(staticDecorations.decorations().keySet())) {
            staticDecorations.decorations().forEach((id, entry) -> {
                if (!this.decorations.containsKey(id)) {
                    self.addDecoration(entry.type(), (LevelAccessor) tickingPlayer.level(), id, entry.x(), entry.z(), entry.rotation(), null);
                }
            });
        }

        // Cancel vanilla execution: avoids redundant carriedBy loop and inventory scans
        ci.cancel();
    }
}
