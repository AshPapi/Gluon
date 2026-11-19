package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.channel.ChannelFutureListener;
import java.util.concurrent.atomic.AtomicInteger;
import net.ashpapi.papioptimization.network.PapiNetworkClassifier;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Connection.class, priority = 1500)
public abstract class ConnectionFlushConsolidationMixin {

    @Shadow
    private int tickCount;

    @Shadow
    public abstract boolean isMemoryConnection();

    @Shadow
    public abstract boolean isConnected();

    @Unique
    private static final int PAPI$MAX_CONSOLIDATED_WRITES = 32;

    @Unique
    private final AtomicInteger papi$pendingWrites = new AtomicInteger(0);

    @WrapMethod(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V")
    private void papi$consolidateFlush(
            Packet<?> packet,
            @Nullable ChannelFutureListener listener,
            boolean flush,
            Operation<Void> original
    ) {
        if (flush
                && listener == null
                && this.tickCount > 0
                && this.isConnected()
                && !this.isMemoryConnection()
                && !PapiNetworkClassifier.isImmediateFlushPacket(packet)) {
            int prev = this.papi$pendingWrites.getAndUpdate(v -> (v + 1 >= PAPI$MAX_CONSOLIDATED_WRITES) ? 0 : (v + 1));
            if (prev + 1 < PAPI$MAX_CONSOLIDATED_WRITES) {
                original.call(packet, null, false);
                return;
            }
            original.call(packet, null, true);
            return;
        }
        if (flush) {
            this.papi$pendingWrites.getAndSet(0);
        }
        original.call(packet, listener, flush);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void papi$resetPendingWritesOnTick(CallbackInfo ci) {
        this.papi$pendingWrites.getAndSet(0);
    }

    @Inject(method = "flushChannel", at = @At("HEAD"))
    private void papi$resetPendingWritesOnFlush(CallbackInfo ci) {
        this.papi$pendingWrites.getAndSet(0);
    }
}
