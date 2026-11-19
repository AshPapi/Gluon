package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Varint21LengthFieldPrepender;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = Varint21LengthFieldPrepender.class, priority = 1500)
public abstract class Varint21LengthFieldPrependerMixin {

    @WrapMethod(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V")
    private void papi$fastEncodeLength(
            ChannelHandlerContext ctx,
            ByteBuf msg,
            ByteBuf out,
            Operation<Void> original
    ) {
        int bodyLen = msg.readableBytes();
        if ((bodyLen & ~0x1FFFFF) != 0) {
            original.call(ctx, msg, out);
            return;
        }
        if (bodyLen < 0x80) {
            out.ensureWritable(1 + bodyLen);
            out.writeByte(bodyLen);
        } else if (bodyLen < 0x4000) {
            out.ensureWritable(2 + bodyLen);
            out.writeShortLE(((bodyLen & 0x7F) | 0x80) | ((bodyLen >>> 7) << 8));
        } else {
            out.ensureWritable(3 + bodyLen);
            out.writeShortLE(((bodyLen & 0x7F) | 0x80) | ((((bodyLen >>> 7) & 0x7F) | 0x80) << 8));
            out.writeByte(bodyLen >>> 14);
        }
        out.writeBytes(msg, msg.readerIndex(), bodyLen);
    }
}
