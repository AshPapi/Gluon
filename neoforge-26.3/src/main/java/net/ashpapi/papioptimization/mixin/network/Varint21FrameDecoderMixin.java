package net.ashpapi.papioptimization.mixin.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.VarInt;
import net.minecraft.network.Varint21FrameDecoder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = Varint21FrameDecoder.class, priority = 1500)
public abstract class Varint21FrameDecoderMixin {

    @Shadow
    @Final
    private BandwidthDebugMonitor monitor;

    @Inject(method = "decode", at = @At("HEAD"), cancellable = true)
    private void papi$fastZeroCopyDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out, CallbackInfo ci) {
        in.markReaderIndex();
        if (!in.isReadable()) {
            in.resetReaderIndex();
            ci.cancel();
            return;
        }
        int length;
        byte b0 = in.readByte();
        if (b0 >= 0) {
            length = b0;
        } else {
            if (!in.isReadable()) {
                in.resetReaderIndex();
                ci.cancel();
                return;
            }
            byte b1 = in.readByte();
            if (b1 >= 0) {
                length = (b0 & 127) | (b1 << 7);
            } else {
                if (!in.isReadable()) {
                    in.resetReaderIndex();
                    ci.cancel();
                    return;
                }
                byte b2 = in.readByte();
                if (b2 >= 0) {
                    length = (b0 & 127) | ((b1 & 127) << 7) | (b2 << 14);
                } else {
                    throw new CorruptedFrameException("length wider than 21-bit");
                }
            }
        }
        if (length <= 0) {
            throw new CorruptedFrameException("Frame length cannot be zero");
        }
        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            ci.cancel();
            return;
        }
        if (this.monitor != null) {
            this.monitor.onReceive(length + VarInt.getByteSize(length));
        }
        out.add(in.readRetainedSlice(length));
        ci.cancel();
    }
}
