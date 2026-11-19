package net.ashpapi.papioptimization.mixin.worldgen;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import net.ashpapi.papioptimization.access.PapiNoisiumPalette;
import net.ashpapi.papioptimization.mixin.chunk.PalettedContainerDataAccessor;
import net.ashpapi.papioptimization.worldgen.NoisiumPaletteCursor;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PaletteResize;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = PalettedContainer.class, priority = 1500)
public abstract class PalettedContainerNoisiumMixin<T> implements PapiNoisiumPalette, PaletteResize<T> {

    @Unique
    private static final VarHandle PAPI$DATA_HANDLE = papi$resolveDataHandle();

    @Unique
    private static VarHandle papi$resolveDataHandle() {
        try {
            Class<?> dataClass = Class.forName("net.minecraft.world.level.chunk.PalettedContainer$Data");
            return MethodHandles.privateLookupIn(PalettedContainer.class, MethodHandles.lookup())
                    .findVarHandle(PalettedContainer.class, "data", dataClass);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to resolve PalettedContainer.data VarHandle", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public BlockState papi$setDirectNoisium(int index, BlockState state, NoisiumPaletteCursor cursor) {
        Object curData = PAPI$DATA_HANDLE.getVolatile(this);
        if (cursor.dataRef != curData) {
            cursor.dataRef = curData;
            cursor.lastState = null;
            cursor.lastId = -1;
            cursor.defaultState = null;
            cursor.defaultId = -1;
            cursor.airId = -1;
        }

        int id;
        if (state == cursor.lastState && cursor.lastId >= 0) {
            id = cursor.lastId;
        } else if (state == cursor.defaultState && cursor.defaultId >= 0) {
            id = cursor.defaultId;
        } else {
            PalettedContainerDataAccessor<BlockState> acc = (PalettedContainerDataAccessor<BlockState>) curData;
            id = acc.papi$getPalette().idFor(state, (PaletteResize<BlockState>) this);
            Object afterData = PAPI$DATA_HANDLE.getVolatile(this);
            if (afterData != curData) {
                curData = afterData;
                cursor.dataRef = curData;
                cursor.defaultState = null;
                cursor.defaultId = -1;
                cursor.airId = -1;
            }
            if (cursor.defaultState == null && state != Blocks.AIR.defaultBlockState()) {
                cursor.defaultState = state;
                cursor.defaultId = id;
            }
            cursor.lastState = state;
            cursor.lastId = id;
        }

        PalettedContainerDataAccessor<BlockState> acc = (PalettedContainerDataAccessor<BlockState>) curData;
        BitStorage storage = acc.papi$getStorage();
        int prevId = storage.getAndSet(index, id);
        if (prevId == id) {
            return state;
        }
        if (prevId == cursor.airId) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState prev = acc.papi$getPalette().valueFor(prevId);
        if (prev == Blocks.AIR.defaultBlockState()) {
            cursor.airId = prevId;
        }
        return prev;
    }
}
