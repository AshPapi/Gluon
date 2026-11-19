package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarInt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = VarInt.class, priority = 1500)
public abstract class VarIntMixin {

    @Unique
    private static final int[] PAPI$VARINT_LENGTHS = new int[33];

    static {
        for (int i = 0; i <= 32; i++) {
            int bits = 32 - i;
            PAPI$VARINT_LENGTHS[i] = bits == 0 ? 1 : (bits + 6) / 7;
        }
    }

    @WrapMethod(method = "getByteSize")
    private static int papi$fastGetByteSize(int value, Operation<Integer> original) {
        return PAPI$VARINT_LENGTHS[Integer.numberOfLeadingZeros(value)];
    }

    @WrapMethod(method = "write")
    private static ByteBuf papi$fastWrite(ByteBuf buf, int value, Operation<ByteBuf> original) {
        if ((value & (0xFFFFFFFF << 7)) == 0) {
            buf.writeByte(value);
        } else if ((value & (0xFFFFFFFF << 14)) == 0) {
            int w = (value & 0x7F | 0x80) << 8 | (value >>> 7);
            buf.writeShort(w);
        } else if ((value & (0xFFFFFFFF << 21)) == 0) {
            int w = (value & 0x7F | 0x80) << 16 | ((value >>> 7) & 0x7F | 0x80) << 8 | (value >>> 14);
            buf.writeMedium(w);
        } else if ((value & (0xFFFFFFFF << 28)) == 0) {
            int w = (value & 0x7F | 0x80) << 24 | (((value >>> 7) & 0x7F | 0x80) << 16)
                    | (((value >>> 14) & 0x7F | 0x80) << 8) | (value >>> 21);
            buf.writeInt(w);
        } else {
            int w = (value & 0x7F | 0x80) << 24 | ((value >>> 7) & 0x7F | 0x80) << 16
                    | ((value >>> 14) & 0x7F | 0x80) << 8 | ((value >>> 21) & 0x7F | 0x80);
            buf.writeInt(w);
            buf.writeByte(value >>> 28);
        }
        return buf;
    }
}
