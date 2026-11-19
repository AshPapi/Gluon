package net.ashpapi.papioptimization.core;

import net.minecraft.core.BlockPos;

public final class BlockPosPool {

    private static final int POOL_SIZE = 16;

    private static final class PoolState {
        final BlockPos.MutableBlockPos[] pool = new BlockPos.MutableBlockPos[POOL_SIZE];
        int depth = 0;

        PoolState() {
            for (int i = 0; i < POOL_SIZE; i++) {
                this.pool[i] = new BlockPos.MutableBlockPos();
            }
        }
    }

    private static final ThreadLocal<PoolState> LOCAL_STATE = ThreadLocal.withInitial(PoolState::new);

    private BlockPosPool() {}

    public static int mark() {
        return LOCAL_STATE.get().depth;
    }

    public static void reset(int mark) {
        LOCAL_STATE.get().depth = mark;
    }

    public static BlockPos.MutableBlockPos get(int x, int y, int z) {
        PoolState state = LOCAL_STATE.get();
        int d = state.depth;
        if (d < POOL_SIZE) {
            state.depth = d + 1;
            return state.pool[d].set(x, y, z);
        }
        return new BlockPos.MutableBlockPos(x, y, z);
    }

    public static BlockPos.MutableBlockPos get() {
        return get(0, 0, 0);
    }
}
