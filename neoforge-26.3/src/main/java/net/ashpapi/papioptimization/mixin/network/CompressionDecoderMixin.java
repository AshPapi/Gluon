package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.Inflater;
import net.ashpapi.papioptimization.network.PapiCompressionPool;
import net.minecraft.network.CompressionDecoder;
import net.minecraft.network.VarInt;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = CompressionDecoder.class, priority = 1500)
public abstract class CompressionDecoderMixin {

    @Shadow
    private int threshold;

    @Shadow
    private boolean validateDecompressed;

    @Unique
    private static final int PAPI$MAX_RETAINED_DIRECT = 65536;

    @Unique
    private @Nullable ByteBuffer papi$directInputCache;

    @WrapMethod(method = "decode")
    private void papi$pooledZeroCopyDecode(
            ChannelHandlerContext ctx,
            ByteBuf in,
            List<Object> out,
            Operation<Void> original
    ) throws Exception {
        if (in.readableBytes() == 0) {
            return;
        }
        int uncompressedSize = VarInt.read(in);
        if (uncompressedSize == 0) {
            out.add(in.readRetainedSlice(in.readableBytes()));
            return;
        }
        if (this.validateDecompressed) {
            if (uncompressedSize < this.threshold) {
                throw new DecoderException("Badly compressed packet - size of " + uncompressedSize + " is below server threshold of " + this.threshold);
            }
            if (uncompressedSize > 8388608) {
                throw new DecoderException("Badly compressed packet - size of " + uncompressedSize + " is larger than protocol maximum of 8388608");
            }
        }
        if (uncompressedSize < 0) {
            throw new DecoderException("Badly compressed packet - negative size " + uncompressedSize);
        }

        Inflater pooledInflater = PapiCompressionPool.acquireInflater();
        ByteBuf target = null;
        try {
            int readable = in.readableBytes();
            if (in.nioBufferCount() == 1) {
                ByteBuffer src = in.nioBuffer();
                in.skipBytes(readable);
                pooledInflater.setInput(src);
            } else if (readable <= PAPI$MAX_RETAINED_DIRECT) {
                ByteBuffer cached = this.papi$directInputCache;
                if (cached == null || cached.capacity() < readable) {
                    int cap = Math.min(Integer.highestOneBit(Math.max(readable - 1, 1024)) << 1, PAPI$MAX_RETAINED_DIRECT);
                    cached = ByteBuffer.allocateDirect(cap);
                    this.papi$directInputCache = cached;
                }
                cached.clear().limit(readable);
                in.readBytes(cached);
                cached.flip();
                pooledInflater.setInput(cached);
            } else {
                ByteBuffer temp = ByteBuffer.allocateDirect(readable);
                in.readBytes(temp);
                temp.flip();
                pooledInflater.setInput(temp);
            }

            target = ctx.alloc().directBuffer(uncompressedSize);
            ByteBuffer dst = target.internalNioBuffer(0, uncompressedSize);
            int startPos = dst.position();
            int totalWritten = 0;
            while (totalWritten < uncompressedSize && !pooledInflater.finished()) {
                int produced = pooledInflater.inflate(dst);
                if (produced == 0) {
                    if (pooledInflater.needsInput() || pooledInflater.needsDictionary()) {
                        break;
                    }
                }
                totalWritten = dst.position() - startPos;
            }
            if (totalWritten != uncompressedSize) {
                throw new DecoderException("Badly compressed packet - actual length of uncompressed payload " + totalWritten + " is does not match declared size " + uncompressedSize);
            }
            target.writerIndex(target.writerIndex() + totalWritten);
            out.add(target);
            target = null;
        } finally {
            if (target != null) {
                target.release();
            }
            PapiCompressionPool.releaseInflater(pooledInflater);
        }
    }
}
