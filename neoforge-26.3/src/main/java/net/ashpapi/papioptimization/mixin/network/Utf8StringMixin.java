package net.ashpapi.papioptimization.mixin.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.Utf8String;
import net.minecraft.network.VarInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Utf8String.class, priority = 1500)
public abstract class Utf8StringMixin {

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private static void papi$zeroAllocWrite(ByteBuf buf, CharSequence input, int maxLength, CallbackInfo ci) {
        int charLen = input.length();
        if (charLen > maxLength) {
            throw new EncoderException("String too big (was " + charLen + " characters, max " + maxLength + ")");
        }
        int maxPossibleBytes = ByteBufUtil.utf8MaxBytes(input);
        int maxAllowedBytes = ByteBufUtil.utf8MaxBytes(maxLength);
        if (maxPossibleBytes <= 127) {
            buf.ensureWritable(1 + maxPossibleBytes);
            int lengthIdx = buf.writerIndex();
            buf.writeByte(0);
            int written = ByteBufUtil.writeUtf8(buf, input);
            if (written > maxAllowedBytes) {
                throw new EncoderException("String too big (was " + written + " bytes encoded, max " + maxAllowedBytes + ")");
            }
            buf.setByte(lengthIdx, written);
            ci.cancel();
            return;
        }
        int encodedBytes = ByteBufUtil.utf8Bytes(input);
        if (encodedBytes > maxAllowedBytes) {
            throw new EncoderException("String too big (was " + encodedBytes + " bytes encoded, max " + maxAllowedBytes + ")");
        }
        buf.ensureWritable(VarInt.getByteSize(encodedBytes) + encodedBytes);
        VarInt.write(buf, encodedBytes);
        ByteBufUtil.writeUtf8(buf, input);
        ci.cancel();
    }
}
