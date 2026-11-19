package net.ashpapi.papioptimization.worldgen;

import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class NoisiumPaletteCursor {

    public @Nullable Object dataRef;
    public @Nullable BlockState lastState;
    public int lastId = -1;
    public @Nullable BlockState defaultState;
    public int defaultId = -1;
    public int airId = -1;

    public void reset() {
        this.dataRef = null;
        this.lastState = null;
        this.lastId = -1;
        this.defaultState = null;
        this.defaultId = -1;
        this.airId = -1;
    }
}
