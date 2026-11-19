package net.ashpapi.papioptimization.mixin.sculk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import net.ashpapi.papioptimization.sculk.IChunkListenerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.debug.DebugGameEventInfo;
import net.minecraft.util.debug.DebugSubscriptions;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.GameEventListenerRegistry;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = GameEventDispatcher.class, priority = 1500)
public abstract class GameEventDispatcherMixin {
    @Shadow @Final
    private ServerLevel level;

    @Shadow
    protected abstract void handleGameEventMessagesInQueue(List<GameEvent.ListenerInfo> listenerInfos);

    /**
     * @author AshPapi
     * @reason Fast GameEvent dispatching: skips empty chunks and sections, avoids computeIfAbsent allocations,
     * and lazily creates the BY_DISTANCE queue. 100% vanilla parity.
     */
    @WrapMethod(method = "post")
    private void papi$fastPost(
            Holder<GameEvent> gameEvent,
            Vec3 position,
            GameEvent.Context context,
            Operation<Void> original
    ) {
        int radius = gameEvent.value().notificationRadius();
        int centerX = (int) Math.floor(position.x);
        int centerY = (int) Math.floor(position.y);
        int centerZ = (int) Math.floor(position.z);

        int sectionMinX = SectionPos.blockToSectionCoord(centerX - radius);
        int sectionMinY = SectionPos.blockToSectionCoord(centerY - radius);
        int sectionMinZ = SectionPos.blockToSectionCoord(centerZ - radius);
        int sectionMaxX = SectionPos.blockToSectionCoord(centerX + radius);
        int sectionMaxY = SectionPos.blockToSectionCoord(centerY + radius);
        int sectionMaxZ = SectionPos.blockToSectionCoord(centerZ + radius);

        @SuppressWarnings("unchecked")
        List<GameEvent.ListenerInfo>[] queueRef = (List<GameEvent.ListenerInfo>[]) new List<?>[1];

        GameEventListenerRegistry.ListenerVisitor visitListeners = (listener, pos) -> {
            if (listener.getDeliveryMode() == GameEventListener.DeliveryMode.BY_DISTANCE) {
                if (queueRef[0] == null) {
                    queueRef[0] = new ArrayList<>();
                }
                queueRef[0].add(new GameEvent.ListenerInfo(gameEvent, position, context, listener, pos));
            } else {
                listener.handleGameEvent(this.level, gameEvent, context, position);
            }
        };

        boolean applicable = false;

        for (int chunkX = sectionMinX; chunkX <= sectionMaxX; ++chunkX) {
            for (int chunkZ = sectionMinZ; chunkZ <= sectionMaxZ; ++chunkZ) {
                LevelChunk chunk = this.level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;

                IChunkListenerAccess tracker = (IChunkListenerAccess) chunk;
                if (!tracker.papi$hasAnyListenerRegistries()) {
                    // No game event listeners registered anywhere in this chunk!
                    continue;
                }

                for (int section = sectionMinY; section <= sectionMaxY; ++section) {
                    GameEventListenerRegistry registry = tracker.papi$getListenerRegistryIfPresent(section);
                    if (registry == null || registry.isEmpty()) {
                        // Section has no registered listeners
                        continue;
                    }
                    applicable |= registry.visitInRangeListeners(gameEvent, position, context, visitListeners);
                }
            }
        }

        if (queueRef[0] != null && !queueRef[0].isEmpty()) {
            this.handleGameEventMessagesInQueue(queueRef[0]);
        }

        if (applicable) {
            this.level.debugSynchronizers().broadcastEventToTracking(
                BlockPos.containing(position.x, position.y, position.z),
                DebugSubscriptions.GAME_EVENTS,
                new DebugGameEventInfo(gameEvent, position)
            );
        }
    }
}
