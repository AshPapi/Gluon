package net.ashpapi.papioptimization.mixin.piston;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockEventData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ServerLevel.class, priority = 1500)
public abstract class ServerLevelBlockEventsMixin {
    @Unique
    private static final int PAPI$MAX_BLOCK_EVENTS_PER_TICK = 65536;

    @Shadow @Final private ObjectLinkedOpenHashSet<BlockEventData> blockEvents;
    @Shadow @Final private List<BlockEventData> blockEventsToReschedule;
    @Shadow @Final private List<ServerPlayer> players;

    @Shadow private boolean doBlockEvent(BlockEventData eventData) {
        throw new AssertionError();
    }

    /**
     * @author AshPapi
     * @reason Optimize block event queue:
     *         1. Circuit breaker (65536 limit) preventing server thread hang from infinite-loop lag machines.
     *         2. Elide ClientboundBlockEventPacket allocation if no players are within 64 blocks.
     *         3. Avoid set re-insertion overhead when no events were rescheduled.
     */
    @WrapMethod(method = "runBlockEvents")
    private void papi$fastRunBlockEvents(Operation<Void> original) {
        ServerLevel self = (ServerLevel) (Object) this;
        this.blockEventsToReschedule.clear();
        int processed = 0;

        while (!this.blockEvents.isEmpty()) {
            if (++processed > PAPI$MAX_BLOCK_EVENTS_PER_TICK) {
                // Safeguard against runaway block event lag machines; remaining events process next tick
                break;
            }
            BlockEventData eventData = this.blockEvents.removeFirst();
            if (self.shouldTickBlocksAt(eventData.pos())) {
                if (!this.doBlockEvent(eventData)) {
                    continue;
                }
                List<ServerPlayer> levelPlayers = this.players;
                if (!levelPlayers.isEmpty()) {
                    double px = (double) eventData.pos().getX();
                    double py = (double) eventData.pos().getY();
                    double pz = (double) eventData.pos().getZ();
                    ClientboundBlockEventPacket packet = null;
                    for (int i = 0, size = levelPlayers.size(); i < size; i++) {
                        ServerPlayer player = levelPlayers.get(i);
                        double dx = px - player.getX();
                        double dy = py - player.getY();
                        double dz = pz - player.getZ();
                        if (dx * dx + dy * dy + dz * dz < 4096.0) {
                            if (packet == null) {
                                packet = new ClientboundBlockEventPacket(
                                        eventData.pos(), eventData.block(), eventData.paramA(), eventData.paramB()
                                );
                            }
                            player.connection.send(packet);
                        }
                    }
                }
                continue;
            }
            this.blockEventsToReschedule.add(eventData);
        }

        if (!this.blockEventsToReschedule.isEmpty()) {
            this.blockEvents.addAll(this.blockEventsToReschedule);
        }
    }
}
