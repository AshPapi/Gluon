package net.ashpapi.papioptimization.mixin.network;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.VarLong;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = VarLong.class, priority = 1500)
public abstract class VarLongMixin {

    @Unique
    private static final int[] PAPI$VARLONG_LENGTHS = new int[65];

    static {
        for (int i = 0; i <= 64; i++) {
            int bits = 64 - i;
            PAPI$VARLONG_LENGTHS[i] = bits == 0 ? 1 : (bits + 6) / 7;
        }
    }

    /**
     * @author AshPapi
     * @reason Branchless single-instruction LZCNT VarLong size calculation
     */
    @WrapMethod(method = "getByteSize")
    private static int papi$fastGetByteSize(long value, Operation<Integer> original) {
        return PAPI$VARLONG_LENGTHS[Long.numberOfLeadingZeros(value)];
    }

    /**
     * @author AshPapi
     * @reason Fast-path single byte VarLong write without looping
     */
    @WrapMethod(method = "write")
    private static ByteBuf papi$fastWrite(ByteBuf buf, long value, Operation<ByteBuf> original) {
        // Fast-path: values 0..127 fit in a single byte (90%+ of small numbers and lengths)
        if ((value & ~0x7FL) == 0L) {
            buf.writeByte((int) value);
            return buf;
        }

        while ((value & -128L) != 0L) {
            buf.writeByte((int) (value & 127L) | 128);
            value >>>= 7;
        }
        buf.writeByte((int) value);
        return buf;
    }
}
