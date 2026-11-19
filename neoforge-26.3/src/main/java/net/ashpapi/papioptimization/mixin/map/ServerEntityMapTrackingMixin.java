package net.ashpapi.papioptimization.mixin.map;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerEntity.class, priority = 1500)
public abstract class ServerEntityMapTrackingMixin {
    @Shadow @Final private Entity entity;
    @Shadow @Final private ServerLevel level;

    @Unique
    private static final Map<ServerPlayer, Object2IntOpenHashMap<MapId>> PAPI$PLAYER_ACTIVE_FRAME_MAPS =
            Collections.synchronizedMap(new WeakHashMap<>());

    @Unique
    private final Set<ServerPlayer> papi$trackingPlayers = new ReferenceOpenHashSet<>();

    @Unique
    private final ObjectArrayList<ServerPlayer> papi$trackingPlayerList = new ObjectArrayList<>();

    @Unique
    private final Reference2ObjectOpenHashMap<ServerPlayer, MapId> papi$countedFrameMaps =
            new Reference2ObjectOpenHashMap<>();

    @Unique
    private @Nullable MapId papi$lastSyncedFrameMapId;

    @Unique
    private static void papi$incrementFrameMapCount(ServerPlayer player, MapId mapId) {
        synchronized (PAPI$PLAYER_ACTIVE_FRAME_MAPS) {
            PAPI$PLAYER_ACTIVE_FRAME_MAPS
                    .computeIfAbsent(player, ignored -> new Object2IntOpenHashMap<>(4))
                    .addTo(mapId, 1);
        }
    }

    @Unique
    private static void papi$decrementFrameMapCount(ServerPlayer player, MapId mapId) {
        synchronized (PAPI$PLAYER_ACTIVE_FRAME_MAPS) {
            Object2IntOpenHashMap<MapId> counts = PAPI$PLAYER_ACTIVE_FRAME_MAPS.get(player);
            if (counts != null) {
                int remaining = counts.addTo(mapId, -1) - 1;
                if (remaining <= 0) {
                    counts.removeInt(mapId);
                    if (counts.isEmpty()) {
                        PAPI$PLAYER_ACTIVE_FRAME_MAPS.remove(player);
                    }
                }
            }
        }
    }

    @Unique
    private void papi$evictPlayerIfUntracked(ServerPlayer player, MapId mapId) {
        boolean hasOtherPairedFrame = false;
        synchronized (PAPI$PLAYER_ACTIVE_FRAME_MAPS) {
            Object2IntOpenHashMap<MapId> counts = PAPI$PLAYER_ACTIVE_FRAME_MAPS.get(player);
            if (counts != null && counts.getInt(mapId) > 0) {
                hasOtherPairedFrame = true;
            }
        }
        if (!hasOtherPairedFrame && !player.getInventory().hasAnyMatching(s -> mapId.equals(s.get(DataComponents.MAP_ID)))) {
            MapItemSavedData data = this.level.getMapData(mapId);
            if (data != null) {
                MapItemSavedDataAccessor accessor = (MapItemSavedDataAccessor) data;
                MapItemSavedData.HoldingPlayer hp = accessor.getCarriedByPlayers().remove(player);
                if (hp != null) {
                    accessor.getCarriedBy().remove(hp);
                }
            }
        }
    }

    @Inject(method = "addPairing", at = @At("HEAD"))
    private void papi$onAddPairing(ServerPlayer player, CallbackInfo ci) {
        boolean added = this.papi$trackingPlayers.add(player);
        if (added) {
            this.papi$trackingPlayerList.add(player);
        }
        if (this.entity instanceof ItemFrame frame && !this.papi$countedFrameMaps.containsKey(player)) {
            MapId mapId = frame.getItem().get(DataComponents.MAP_ID);
            if (added && this.papi$trackingPlayerList.size() == 1) {
                this.papi$lastSyncedFrameMapId = mapId;
            }
            if (mapId != null) {
                this.papi$countedFrameMaps.put(player, mapId);
                papi$incrementFrameMapCount(player, mapId);
            }
        }
    }

    @Inject(method = "removePairing", at = @At("HEAD"))
    private void papi$onRemovePairing(ServerPlayer player, CallbackInfo ci) {
        if (this.papi$trackingPlayers.remove(player)) {
            int idx = this.papi$trackingPlayerList.indexOf(player);
            if (idx >= 0) {
                int last = this.papi$trackingPlayerList.size() - 1;
                if (idx != last) {
                    this.papi$trackingPlayerList.set(idx, this.papi$trackingPlayerList.get(last));
                }
                this.papi$trackingPlayerList.remove(last);
            }
        }

        if (this.entity instanceof ItemFrame frame) {
            MapId countedId = this.papi$countedFrameMaps.remove(player);
            if (countedId != null) {
                papi$decrementFrameMapCount(player, countedId);
                this.papi$evictPlayerIfUntracked(player, countedId);
            }

            ItemStack itemStack = frame.getItem();
            MapId currentId = itemStack.get(DataComponents.MAP_ID);
            if (currentId != null && !currentId.equals(countedId)) {
                this.papi$evictPlayerIfUntracked(player, currentId);
            }

            if (this.papi$trackingPlayerList.isEmpty()) {
                this.papi$lastSyncedFrameMapId = null;
            }
        }
    }

    @Inject(method = "sendChanges", at = @At("HEAD"))
    private void papi$syncFrameMapIdOnChanges(CallbackInfo ci) {
        if (!(this.entity instanceof ItemFrame frame) || this.papi$trackingPlayerList.isEmpty()) {
            return;
        }
        MapId currentId = frame.getItem().get(DataComponents.MAP_ID);
        if (Objects.equals(currentId, this.papi$lastSyncedFrameMapId)) {
            return;
        }
        this.papi$lastSyncedFrameMapId = currentId;
        for (int i = 0, n = this.papi$trackingPlayerList.size(); i < n; i++) {
            ServerPlayer player = this.papi$trackingPlayerList.get(i);
            MapId oldId = this.papi$countedFrameMaps.remove(player);
            if (Objects.equals(oldId, currentId)) {
                if (currentId != null) {
                    this.papi$countedFrameMaps.put(player, currentId);
                }
                continue;
            }
            if (oldId != null) {
                papi$decrementFrameMapCount(player, oldId);
                this.papi$evictPlayerIfUntracked(player, oldId);
            }
            if (currentId != null) {
                this.papi$countedFrameMaps.put(player, currentId);
                papi$incrementFrameMapCount(player, currentId);
            }
        }
    }

    @WrapOperation(
        method = "sendChanges",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;players()Ljava/util/List;")
    )
    private List<ServerPlayer> papi$wrapTrackingPlayers(ServerLevel serverLevel, Operation<List<ServerPlayer>> original) {
        if (this.entity instanceof ItemFrame) {
            return this.papi$trackingPlayerList.isEmpty()
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(this.papi$trackingPlayerList);
        }
        return original.call(serverLevel);
    }
}
