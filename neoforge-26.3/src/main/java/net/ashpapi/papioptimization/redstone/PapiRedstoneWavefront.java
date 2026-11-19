package net.ashpapi.papioptimization.redstone;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

public final class PapiRedstoneWavefront {

    private static final int MAX_DEPTH = 4;
    private static final int MAX_CLUSTER_NODES = 8192;
    private static final Direction[] HORIZONTAL = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final Direction[] UPDATE_ORDER = NeighborUpdater.UPDATE_ORDER;

    private static final ThreadLocal<SolverStack> SOLVER_STACK = ThreadLocal.withInitial(SolverStack::new);

    private static final class SolverStack {
        final Solver[] frames = new Solver[MAX_DEPTH];
        int depth = 0;

        SolverStack() {
            for (int i = 0; i < MAX_DEPTH; i++) {
                this.frames[i] = new Solver();
            }
        }
    }

    private static final class Solver {
        final Long2IntOpenHashMap nodeIndex = new Long2IntOpenHashMap(256);
        final LongArrayList positions = new LongArrayList(256);
        final ArrayList<BlockState> states = new ArrayList<>(256);
        int[] oldPower = new int[256];
        int[] newPower = new int[256];
        boolean[] inCluster = new boolean[256];
        boolean[] queued = new boolean[256];

        final int[][] buckets = new int[16][64];
        final int[] bucketSizes = new int[16];
        int[] bfsQueue = new int[512];
        final LongLinkedOpenHashSet notifyOrder = new LongLinkedOpenHashSet(256);
        final Long2IntOpenHashMap notifyDir = new Long2IntOpenHashMap(256);
        final LongOpenHashSet notifyCenters = new LongOpenHashSet(256);
        final BlockPos.MutableBlockPos mutPos = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos mutNbr = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos mutStep = new BlockPos.MutableBlockPos();

        Solver() {
            this.nodeIndex.defaultReturnValue(-1);
            this.notifyDir.defaultReturnValue(-1);
        }

        void reset() {
            this.nodeIndex.clear();
            this.positions.clear();
            this.states.clear();
            this.notifyOrder.clear();
            this.notifyDir.clear();
            this.notifyCenters.clear();
            for (int p = 0; p < 16; p++) {
                this.bucketSizes[p] = 0;
            }
        }

        void ensureCapacity(int cap) {
            if (cap <= this.oldPower.length) {
                return;
            }
            int nextCap = Math.max(cap, this.oldPower.length << 1);
            this.oldPower = Arrays.copyOf(this.oldPower, nextCap);
            this.newPower = Arrays.copyOf(this.newPower, nextCap);
            this.inCluster = Arrays.copyOf(this.inCluster, nextCap);
            this.queued = Arrays.copyOf(this.queued, nextCap);
        }

        void enqueueBfs(int tail, int vIdx) {
            if (tail == this.bfsQueue.length) {
                this.bfsQueue = Arrays.copyOf(this.bfsQueue, this.bfsQueue.length << 1);
            }
            this.bfsQueue[tail] = vIdx;
        }

        void pushBucket(int power, int idx) {
            int clamped = power < 0 ? 0 : (power > 15 ? 15 : power);
            int size = this.bucketSizes[clamped];
            int[] arr = this.buckets[clamped];
            if (size == arr.length) {
                arr = Arrays.copyOf(arr, size << 1);
                this.buckets[clamped] = arr;
            }
            arr[size] = idx;
            this.bucketSizes[clamped] = size + 1;
        }

        int getOrAddNode(long packed, BlockState state, boolean member) {
            int idx = this.nodeIndex.get(packed);
            if (idx >= 0) {
                if (member && !this.inCluster[idx]) {
                    this.inCluster[idx] = true;
                }
                return idx;
            }
            idx = this.positions.size();
            this.ensureCapacity(idx + 1);
            this.nodeIndex.put(packed, idx);
            this.positions.add(packed);
            this.states.add(state);
            int p = state.getValue(RedstoneWireBlock.POWER);
            this.oldPower[idx] = p;
            this.newPower[idx] = member ? 0 : p;
            this.inCluster[idx] = member;
            this.queued[idx] = false;
            return idx;
        }

        boolean solve(RedstoneWireBlock wireBlock, Level level, BlockPos rootPos, BlockState rootState, @Nullable Orientation orientation) {
            if (level.getBlockState(rootPos) != rootState) {
                return false;
            }
            this.reset();

            int rootIdx = this.getOrAddNode(rootPos.asLong(), rootState, true);
            int rootOld = this.oldPower[rootIdx];
            int rootDirect = computeDirectInput(wireBlock, level, rootPos.getX(), rootPos.getY(), rootPos.getZ(), false);

            if (rootDirect == rootOld) {
                return true;
            }

            if (rootDirect > rootOld) {
                this.newPower[rootIdx] = rootDirect;
                this.pushBucket(rootDirect, rootIdx);
                if (!this.drainWavefront(wireBlock, level)) {
                    return false;
                }
            } else {
                if (!this.collectDepowerCluster(wireBlock, level, rootIdx)) {
                    return false;
                }
                this.seedClusterBoundaryAndDirect(wireBlock, level);
                if (!this.drainWavefront(wireBlock, level)) {
                    return false;
                }
            }

            this.commitAndNotify(wireBlock, level, orientation);
            return true;
        }

        private int computeDirectInput(RedstoneWireBlock wireBlock, Level level, int x, int y, int z, boolean ignoreWiresInCluster) {
            int blockSig = wireBlock.getBlockSignal(level, this.mutPos.set(x, y, z));
            if (blockSig >= 15) {
                return 15;
            }
            if (blockSig < 0) {
                blockSig = 0;
            }
            int maxWire = 0;
            this.mutPos.set(x, y + 1, z);
            boolean aboveConductor = level.getBlockState(this.mutPos).isRedstoneConductor(level, this.mutPos);

            for (int d = 0; d < 4; d++) {
                Direction dir = HORIZONTAL[d];
                int nx = x + dir.getStepX();
                int nz = z + dir.getStepZ();
                this.mutNbr.set(nx, y, nz);
                BlockState nState = level.getBlockState(this.mutNbr);
                if (nState.is(wireBlock)) {
                    int np = resolveNodePower(this.mutNbr.asLong(), nState, ignoreWiresInCluster);
                    if (np > maxWire) maxWire = np;
                }
                boolean nbrConductor = nState.isRedstoneConductor(level, this.mutNbr);
                if (nbrConductor && !aboveConductor) {
                    this.mutStep.set(nx, y + 1, nz);
                    BlockState upState = level.getBlockState(this.mutStep);
                    if (upState.is(wireBlock)) {
                        int np = resolveNodePower(this.mutStep.asLong(), upState, ignoreWiresInCluster);
                        if (np > maxWire) maxWire = np;
                    }
                } else if (!nbrConductor) {
                    this.mutStep.set(nx, y - 1, nz);
                    BlockState downState = level.getBlockState(this.mutStep);
                    if (downState.is(wireBlock)) {
                        int np = resolveNodePower(this.mutStep.asLong(), downState, ignoreWiresInCluster);
                        if (np > maxWire) maxWire = np;
                    }
                }
            }
            return Math.max(blockSig, Math.max(0, maxWire - 1));
        }

        private int resolveNodePower(long packed, BlockState state, boolean ignoreWiresInCluster) {
            int idx = this.nodeIndex.get(packed);
            if (idx >= 0) {
                if (ignoreWiresInCluster && this.inCluster[idx]) {
                    return 0;
                }
                return this.newPower[idx];
            }
            return state.getValue(RedstoneWireBlock.POWER);
        }

        private boolean collectDepowerCluster(RedstoneWireBlock wireBlock, Level level, int rootIdx) {
            int rootOld = this.oldPower[rootIdx];
            int head = 0;
            int tail = 0;
            this.bfsQueue[tail++] = rootIdx;
            this.queued[rootIdx] = true;

            while (head < tail) {
                if (this.positions.size() >= MAX_CLUSTER_NODES) {
                    return false;
                }
                int uIdx = this.bfsQueue[head++];
                long uPacked = this.positions.getLong(uIdx);
                int ux = BlockPos.getX(uPacked);
                int uy = BlockPos.getY(uPacked);
                int uz = BlockPos.getZ(uPacked);

                this.mutPos.set(ux, uy - 1, uz);
                boolean belowUConductor = level.getBlockState(this.mutPos).isRedstoneConductor(level, this.mutPos);

                this.mutPos.set(ux, uy + 1, uz);
                boolean aboveUConductor = level.getBlockState(this.mutPos).isRedstoneConductor(level, this.mutPos);

                for (int d = 0; d < 4; d++) {
                    Direction dir = HORIZONTAL[d];
                    int vx = ux + dir.getStepX();
                    int vz = uz + dir.getStepZ();

                    this.mutNbr.set(vx, uy, vz);
                    BlockState sideState = level.getBlockState(this.mutNbr);
                    if (sideState.is(wireBlock)) {
                        int vOld = sideState.getValue(RedstoneWireBlock.POWER);
                        if (vOld > 0 && vOld <= rootOld) {
                            int vIdx = this.getOrAddNode(this.mutNbr.asLong(), sideState, true);
                            if (!this.queued[vIdx]) {
                                this.queued[vIdx] = true;
                                this.enqueueBfs(tail++, vIdx);
                            }
                        }
                    }

                    if (!aboveUConductor) {
                        this.mutStep.set(vx, uy + 1, vz);
                        BlockState upWire = level.getBlockState(this.mutStep);
                        if (upWire.is(wireBlock)) {
                            int vOld = upWire.getValue(RedstoneWireBlock.POWER);
                            if (vOld > 0 && vOld <= rootOld) {
                                int vIdx = this.getOrAddNode(this.mutStep.asLong(), upWire, true);
                                if (!this.queued[vIdx]) {
                                    this.queued[vIdx] = true;
                                    this.enqueueBfs(tail++, vIdx);
                                }
                            }
                        }
                    }

                    if (belowUConductor && !sideState.isRedstoneConductor(level, this.mutNbr)) {
                        this.mutStep.set(vx, uy - 1, vz);
                        BlockState downWire = level.getBlockState(this.mutStep);
                        if (downWire.is(wireBlock)) {
                            int vOld = downWire.getValue(RedstoneWireBlock.POWER);
                            if (vOld > 0 && vOld <= rootOld) {
                                int vIdx = this.getOrAddNode(this.mutStep.asLong(), downWire, true);
                                if (!this.queued[vIdx]) {
                                    this.queued[vIdx] = true;
                                    this.enqueueBfs(tail++, vIdx);
                                }
                            }
                        }
                    }
                }
            }
            return true;
        }

        private void seedClusterBoundaryAndDirect(RedstoneWireBlock wireBlock, Level level) {
            int count = this.positions.size();
            for (int i = 0; i < count; i++) {
                if (!this.inCluster[i]) {
                    continue;
                }
                long packed = this.positions.getLong(i);
                int x = BlockPos.getX(packed);
                int y = BlockPos.getY(packed);
                int z = BlockPos.getZ(packed);
                int direct = computeDirectInput(wireBlock, level, x, y, z, true);
                this.newPower[i] = direct;
                if (direct > 0) {
                    this.pushBucket(direct, i);
                }
            }
        }

        private boolean drainWavefront(RedstoneWireBlock wireBlock, Level level) {
            for (int p = 15; p >= 2; p--) {
                int[] bucket = this.buckets[p];
                int cursor = 0;
                while (cursor < this.bucketSizes[p]) {
                    if (this.positions.size() >= MAX_CLUSTER_NODES) {
                        return false;
                    }
                    int uIdx = bucket[cursor++];
                    if (this.newPower[uIdx] != p) {
                        continue;
                    }
                    int nextPower = p - 1;
                    long uPacked = this.positions.getLong(uIdx);
                    int ux = BlockPos.getX(uPacked);
                    int uy = BlockPos.getY(uPacked);
                    int uz = BlockPos.getZ(uPacked);
                    this.relaxOutgoing(wireBlock, level, ux, uy, uz, nextPower);
                    bucket = this.buckets[p];
                }
            }
            return true;
        }

        private void relaxOutgoing(
                RedstoneWireBlock wireBlock,
                Level level,
                int ux,
                int uy,
                int uz,
                int nextPower
        ) {
            this.mutPos.set(ux, uy - 1, uz);
            boolean belowUConductor = level.getBlockState(this.mutPos).isRedstoneConductor(level, this.mutPos);

            this.mutPos.set(ux, uy + 1, uz);
            boolean aboveUConductor = level.getBlockState(this.mutPos).isRedstoneConductor(level, this.mutPos);

            for (int d = 0; d < 4; d++) {
                Direction dir = HORIZONTAL[d];
                int vx = ux + dir.getStepX();
                int vz = uz + dir.getStepZ();

                this.mutNbr.set(vx, uy, vz);
                BlockState sideState = level.getBlockState(this.mutNbr);
                if (sideState.is(wireBlock)) {
                    this.tryRelaxTarget(this.mutNbr.asLong(), sideState, nextPower);
                }

                if (!aboveUConductor) {
                    this.mutStep.set(vx, uy + 1, vz);
                    BlockState upWire = level.getBlockState(this.mutStep);
                    if (upWire.is(wireBlock)) {
                        this.tryRelaxTarget(this.mutStep.asLong(), upWire, nextPower);
                    }
                }

                if (belowUConductor && !sideState.isRedstoneConductor(level, this.mutNbr)) {
                    this.mutStep.set(vx, uy - 1, vz);
                    BlockState downWire = level.getBlockState(this.mutStep);
                    if (downWire.is(wireBlock)) {
                        this.tryRelaxTarget(this.mutStep.asLong(), downWire, nextPower);
                    }
                }
            }
        }

        private void tryRelaxTarget(long vPacked, BlockState vState, int nextPower) {
            int vIdx = this.nodeIndex.get(vPacked);
            if (vIdx >= 0) {
                int curNew = this.inCluster[vIdx] ? this.newPower[vIdx] : this.oldPower[vIdx];
                if (nextPower > curNew) {
                    this.inCluster[vIdx] = true;
                    this.newPower[vIdx] = nextPower;
                    this.pushBucket(nextPower, vIdx);
                }
            } else {
                int vOld = vState.getValue(RedstoneWireBlock.POWER);
                if (nextPower > vOld) {
                    int idx = this.getOrAddNode(vPacked, vState, true);
                    this.newPower[idx] = nextPower;
                    this.pushBucket(nextPower, idx);
                }
            }
        }

        private void queueNotify(long packedPos, int dirOrdinal) {
            if (this.nodeIndex.get(packedPos) >= 0) {
                return;
            }
            if (this.notifyOrder.add(packedPos)) {
                this.notifyDir.put(packedPos, dirOrdinal);
            }
        }

        private void commitAndNotify(RedstoneWireBlock wireBlock, Level level, @Nullable Orientation baseOrientation) {
            int count = this.positions.size();
            boolean anyChanged = false;
            for (int i = 0; i < count; i++) {
                if (!this.inCluster[i]) {
                    continue;
                }
                int prev = this.oldPower[i];
                int next = this.newPower[i];
                if (prev == next) {
                    continue;
                }
                anyChanged = true;
                long packed = this.positions.getLong(i);
                int x = BlockPos.getX(packed);
                int y = BlockPos.getY(packed);
                int z = BlockPos.getZ(packed);
                this.mutPos.set(x, y, z);
                BlockState expected = this.states.get(i);
                if (level.getBlockState(this.mutPos) == expected) {
                    level.setBlock(BlockPos.of(packed), expected.setValue(RedstoneWireBlock.POWER, next), 2);
                }
            }
            if (!anyChanged) {
                return;
            }

            for (int i = 0; i < count; i++) {
                if (!this.inCluster[i] || this.oldPower[i] == this.newPower[i]) {
                    continue;
                }
                long packed = this.positions.getLong(i);
                if (!this.notifyCenters.add(packed)) {
                    continue;
                }
                BlockPos centerPos = BlockPos.of(packed);
                if (EventHooks.onNeighborNotify(level, centerPos, level.getBlockState(centerPos), EnumSet.allOf(Direction.class), false).isCanceled()) {
                    continue;
                }
                int x = BlockPos.getX(packed);
                int y = BlockPos.getY(packed);
                int z = BlockPos.getZ(packed);
                for (int d = 0; d < 6; d++) {
                    Direction dir = UPDATE_ORDER[d];
                    long nPacked = BlockPos.asLong(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
                    this.queueNotify(nPacked, d);
                }
            }

            for (int i = 0; i < count; i++) {
                if (!this.inCluster[i] || this.oldPower[i] == this.newPower[i]) {
                    continue;
                }
                long packed = this.positions.getLong(i);
                int x = BlockPos.getX(packed);
                int y = BlockPos.getY(packed);
                int z = BlockPos.getZ(packed);
                for (int d = 0; d < 6; d++) {
                    Direction dir = UPDATE_ORDER[d];
                    int nx = x + dir.getStepX();
                    int ny = y + dir.getStepY();
                    int nz = z + dir.getStepZ();
                    long nPacked = BlockPos.asLong(nx, ny, nz);
                    if (!this.notifyCenters.add(nPacked)) {
                        continue;
                    }
                    BlockPos centerPos = BlockPos.of(nPacked);
                    if (EventHooks.onNeighborNotify(level, centerPos, level.getBlockState(centerPos), EnumSet.allOf(Direction.class), false).isCanceled()) {
                        continue;
                    }
                    for (int d2 = 0; d2 < 6; d2++) {
                        Direction dir2 = UPDATE_ORDER[d2];
                        int sx = nx + dir2.getStepX();
                        int sy = ny + dir2.getStepY();
                        int sz = nz + dir2.getStepZ();
                        long sPacked = BlockPos.asLong(sx, sy, sz);
                        if (sPacked == packed) {
                            continue;
                        }
                        this.queueNotify(sPacked, d2);
                    }
                }
            }

            if (!this.notifyOrder.isEmpty()) {
                var it = this.notifyOrder.iterator();
                while (it.hasNext()) {
                    long np = it.nextLong();
                    int dirOrd = this.notifyDir.get(np);
                    Direction front = (dirOrd >= 0 && dirOrd < UPDATE_ORDER.length) ? UPDATE_ORDER[dirOrd] : null;
                    Orientation orient = baseOrientation != null && front != null
                            ? ExperimentalRedstoneUtils.withFront(baseOrientation, front)
                            : ExperimentalRedstoneUtils.initialOrientation(level, front, null);
                    BlockPos targetPos = BlockPos.of(np);
                    BlockState targetState = level.getBlockState(targetPos);
                    if (!targetState.isAir() && !(targetState.is(wireBlock) && this.nodeIndex.get(np) >= 0)) {
                        level.neighborChanged(targetPos, wireBlock, orient);
                    }
                }
            }
        }
    }

    private PapiRedstoneWavefront() {}

    public static boolean updatePowerStrength(
            RedstoneWireBlock wireBlock,
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable Orientation orientation
    ) {
        SolverStack stack = SOLVER_STACK.get();
        if (stack.depth >= MAX_DEPTH) {
            return false;
        }
        Solver solver = stack.frames[stack.depth++];
        try {
            return solver.solve(wireBlock, level, pos, state, orientation);
        } finally {
            solver.reset();
            stack.depth--;
        }
    }
}
