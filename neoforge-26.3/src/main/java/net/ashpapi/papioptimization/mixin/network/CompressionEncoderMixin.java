package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;
import net.ashpapi.papioptimization.network.PapiCompressionPool;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.VarInt;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CompressionEncoder.class, priority = 1500)
public abstract class CompressionEncoderMixin {

    @Shadow
    @Final
    private byte[] encodeBuf;

    @Shadow
    @Final
    private Deflater deflater;

    @Shadow
    private int threshold;

    @Unique
    private static final int PAPI$MAX_RETAINED_SCRATCH = 65536;

    @Unique
    private byte @Nullable [] papi$inputScratch;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void papi$releaseUnusedVanillaDeflater(int threshold, CallbackInfo ci) {
        if (this.deflater != null) {
            this.deflater.end();
        }
    }

    @WrapMethod(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V")
    private void papi$zeroAllocEncode(
            ChannelHandlerContext ctx,
            ByteBuf in,
            ByteBuf out,
            Operation<Void> original
    ) {
        int readable = in.readableBytes();
        if (readable > 8388608) {
            throw new IllegalArgumentException("Packet too big (is " + readable + ", should be less than 8388608)");
        }
        if (readable < this.threshold) {
            VarInt.write(out, 0);
            out.writeBytes(in);
            return;
        }
        VarInt.write(out, readable);
        Deflater pooledDeflater = PapiCompressionPool.acquireDeflater();
        try {
            if (in.nioBufferCount() == 1) {
                ByteBuffer srcBuf = in.nioBuffer();
                pooledDeflater.setInput(srcBuf);
                in.skipBytes(readable);
            } else {
                byte[] tmp = this.papi$acquireScratch(readable);
                in.readBytes(tmp, 0, readable);
                pooledDeflater.setInput(tmp, 0, readable);
            }
            pooledDeflater.finish();
            boolean directOut = !(out instanceof CompositeByteBuf) && out.nioBufferCount() == 1;
            int chunk = Math.min(Math.max(readable + 32, 256), PAPI$MAX_RETAINED_SCRATCH);
            while (!pooledDeflater.finished()) {
                if (directOut) {
                    out.ensureWritable(chunk);
                    if (out.nioBufferCount() == 1) {
                        int wi = out.writerIndex();
                        int avail = out.writableBytes();
                        ByteBuffer dstBuf = out.internalNioBuffer(wi, avail);
                        int written = pooledDeflater.deflate(dstBuf);
                        if (written > 0) {
                            out.writerIndex(wi + written);
                        } else if (!pooledDeflater.finished()) {
                            int fallback = pooledDeflater.deflate(this.encodeBuf);
                            if (fallback > 0) {
                                out.writeBytes(this.encodeBuf, 0, fallback);
                            }
                        }
                        chunk = Math.min(chunk << 1, PAPI$MAX_RETAINED_SCRATCH);
                        continue;
                    }
                    directOut = false;
                }
                int written = pooledDeflater.deflate(this.encodeBuf);
                if (written > 0) {
                    out.writeBytes(this.encodeBuf, 0, written);
                }
            }
        } finally {
            PapiCompressionPool.releaseDeflater(pooledDeflater);
        }
    }

    @Unique
    private byte[] papi$acquireScratch(int needed) {
        if (needed > PAPI$MAX_RETAINED_SCRATCH) {
            return new byte[needed];
        }
        byte[] buf = this.papi$inputScratch;
        if (buf == null || buf.length < needed) {
            int alloc = Integer.highestOneBit(Math.max(needed - 1, 1024)) << 1;
            buf = new byte[Math.min(alloc, PAPI$MAX_RETAINED_SCRATCH)];
            this.papi$inputScratch = buf;
        }
        return buf;
    }
}
