package net.ashpapi.papioptimization.client.occlusion;

import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import net.ashpapi.papioptimization.mixin.chunk.PalettedContainerDataAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class PapiOcclusionEngine {

    public static final byte TARGET_ENTITY = 1;
    public static final byte TARGET_BLOCK_ENTITY = 2;
    public static final byte TARGET_PARTICLE = 3;

    private static final int HYST_TABLE_SIZE = 4096;
    private static final int HYST_TABLE_MASK = HYST_TABLE_SIZE - 1;
    private static final int HYST_HOLD_FRAMES = 2;
    private static final long HYST_FRAME_STEP_NANOS = 8_000_000L;
    private static final double PARTICLE_MIN_CULL_DIST_SQR = 64.0;

    private static final int SECTION_CACHE_SIZE = 2048;
    private static final int SECTION_CACHE_MASK = SECTION_CACHE_SIZE - 1;
    private static final long SECTION_TTL_TICKS = 16L;
    private static final int MAX_BATCH_REQUESTS = 2048;
    private static final int VIS_TABLE_SIZE = 4096;
    private static final int VIS_TABLE_MASK = VIS_TABLE_SIZE - 1;
    private static final long SUBMIT_INTERVAL_NANOS = 6_000_000L;
    private static final long SINGLE_VOXEL_MAX_AGE_TICKS = 5L;
    private static final long MULTI_VOXEL_MAX_AGE_TICKS = 3L;
    private static final double MULTI_VOXEL_MAX_SHIFT_SQR = 0.35 * 0.35;

    private static final byte KIND_EMPTY = 0;
    private static final byte KIND_FULL = 1;
    private static final byte KIND_SPARSE = 2;

    private static final byte STATE_VISIBLE = 0;
    private static final byte STATE_OCCLUDED_SINGLE_VOXEL = 1;
    private static final byte STATE_OCCLUDED_MULTI_VOXEL = 2;

    private static final long[] EMPTY_WORDS = new long[0];

    private static final class SectionEntry {
        long secKey = Long.MIN_VALUE;
        int observedEpoch = 0;
        long expireTick = Long.MIN_VALUE;
        byte kind = KIND_EMPTY;
        long[] words = EMPTY_WORDS;
    }

    private static final class RequestBatch {
        int generation = -1;
        WeakReference<ClientLevel> levelRef = new WeakReference<>(null);
        long gameTime;
        double camX;
        double camY;
        double camZ;
        int count;
        final byte[] kinds = new byte[MAX_BATCH_REQUESTS];
        final long[] keys = new long[MAX_BATCH_REQUESTS];
        final double[] minX = new double[MAX_BATCH_REQUESTS];
        final double[] minY = new double[MAX_BATCH_REQUESTS];
        final double[] minZ = new double[MAX_BATCH_REQUESTS];
        final double[] maxX = new double[MAX_BATCH_REQUESTS];
        final double[] maxY = new double[MAX_BATCH_REQUESTS];
        final double[] maxZ = new double[MAX_BATCH_REQUESTS];
        final int[] seenStamps = new int[VIS_TABLE_SIZE];
        final byte[] seenKinds = new byte[VIS_TABLE_SIZE];
        final long[] seenKeys = new long[VIS_TABLE_SIZE];
        int seenStamp = 1;

        void reset(int gen, @Nullable ClientLevel level, long time, double cx, double cy, double cz) {
            this.generation = gen;
            this.levelRef = new WeakReference<>(level);
            this.gameTime = time;
            this.camX = cx;
            this.camY = cy;
            this.camZ = cz;
            this.count = 0;
            this.seenStamp++;
            if (this.seenStamp == 0) {
                java.util.Arrays.fill(this.seenStamps, 0);
                this.seenStamp = 1;
            }
        }

        void add(byte kind, long key, double bxMin, double byMin, double bzMin, double bxMax, double byMax, double bzMax) {
            int idx = this.count;
            if (idx >= MAX_BATCH_REQUESTS) {
                return;
            }
            int slot = mix64(key ^ ((long) kind << 56)) & VIS_TABLE_MASK;
            int stamp = this.seenStamp;
            for (int probe = 0; probe < 8; probe++) {
                int s = (slot + probe) & VIS_TABLE_MASK;
                if (this.seenStamps[s] != stamp) {
                    this.seenStamps[s] = stamp;
                    this.seenKinds[s] = kind;
                    this.seenKeys[s] = key;
                    break;
                }
                if (this.seenKinds[s] == kind && this.seenKeys[s] == key) {
                    return;
                }
            }
            this.kinds[idx] = kind;
            this.keys[idx] = key;
            this.minX[idx] = bxMin;
            this.minY[idx] = byMin;
            this.minZ[idx] = bzMin;
            this.maxX[idx] = bxMax;
            this.maxY[idx] = byMax;
            this.maxZ[idx] = bzMax;
            this.count = idx + 1;
        }
    }

    private static final class VisibilitySnapshot {
        int generation = -1;
        long gameTime = Long.MIN_VALUE;
        int blockEpoch = 0;
        double camX;
        double camY;
        double camZ;
        int stamp = 1;
        final int[] stamps = new int[VIS_TABLE_SIZE];
        final byte[] kinds = new byte[VIS_TABLE_SIZE];
        final long[] keys = new long[VIS_TABLE_SIZE];
        final byte[] states = new byte[VIS_TABLE_SIZE];
        final long[] blockerPos = new long[VIS_TABLE_SIZE];
        final byte[] cornerBlockerCount = new byte[VIS_TABLE_SIZE];
        final long[] cornerBlockers = new long[VIS_TABLE_SIZE * 8];
        final float[] centerX = new float[VIS_TABLE_SIZE];
        final float[] centerY = new float[VIS_TABLE_SIZE];
        final float[] centerZ = new float[VIS_TABLE_SIZE];

        void reset(int gen, long time, int worldBlockEpoch, double cx, double cy, double cz) {
            this.generation = gen;
            this.gameTime = time;
            this.blockEpoch = worldBlockEpoch;
            this.camX = cx;
            this.camY = cy;
            this.camZ = cz;
            this.stamp++;
            if (this.stamp == 0) {
                java.util.Arrays.fill(this.stamps, 0);
                this.stamp = 1;
            }
        }

        void invalidate() {
            this.generation = -1;
            this.gameTime = Long.MIN_VALUE;
        }

        void put(byte kind, long key, byte state, long blocker, float cx, float cy, float cz) {
            put(kind, key, state, blocker, null, 0, cx, cy, cz);
        }

        void put(
                byte kind,
                long key,
                byte state,
                long blocker,
                long @Nullable [] extraBlockers,
                int extraCount,
                float cx,
                float cy,
                float cz
        ) {
            int slot = mix64(key ^ ((long) kind << 56)) & VIS_TABLE_MASK;
            int st = this.stamp;
            for (int probe = 0; probe < 12; probe++) {
                int s = (slot + probe) & VIS_TABLE_MASK;
                if (this.stamps[s] != st || (this.kinds[s] == kind && this.keys[s] == key)) {
                    this.stamps[s] = st;
                    this.kinds[s] = kind;
                    this.keys[s] = key;
                    this.states[s] = state;
                    this.blockerPos[s] = blocker;
                    int count = (extraBlockers != null && extraCount > 0) ? Math.min(8, extraCount) : 0;
                    this.cornerBlockerCount[s] = (byte) count;
                    if (count > 0) {
                        int base = s << 3;
                        for (int k = 0; k < count; k++) {
                            this.cornerBlockers[base + k] = extraBlockers[k];
                        }
                    }
                    this.centerX[s] = cx;
                    this.centerY[s] = cy;
                    this.centerZ[s] = cz;
                    return;
                }
            }
        }

        int findSlot(byte kind, long key) {
            int slot = mix64(key ^ ((long) kind << 56)) & VIS_TABLE_MASK;
            int st = this.stamp;
            for (int probe = 0; probe < 12; probe++) {
                int s = (slot + probe) & VIS_TABLE_MASK;
                if (this.stamps[s] != st) {
                    return -1;
                }
                if (this.kinds[s] == kind && this.keys[s] == key) {
                    return s;
                }
            }
            return -1;
        }
    }

    private static final AtomicInteger GENERATION = new AtomicInteger(1);
    private static final AtomicInteger WORLD_BLOCK_EPOCH = new AtomicInteger(1);
    private static final AtomicIntegerArray SECTION_EPOCHS = new AtomicIntegerArray(SECTION_CACHE_SIZE);
    private static final @Nullable VarHandle PALETTED_DATA_HANDLE = resolvePalettedDataHandle();
    private static final AtomicInteger EXTERNAL_SHADOW_PASS_DEPTH = new AtomicInteger(0);

    private static @Nullable VarHandle resolvePalettedDataHandle() {
        try {
            Class<?> dataClass = Class.forName("net.minecraft.world.level.chunk.PalettedContainer$Data");
            return MethodHandles.privateLookupIn(PalettedContainer.class, MethodHandles.lookup())
                    .findVarHandle(PalettedContainer.class, "data", dataClass);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void enterShadowPass() {
        EXTERNAL_SHADOW_PASS_DEPTH.incrementAndGet();
    }

    public static void exitShadowPass() {
        EXTERNAL_SHADOW_PASS_DEPTH.updateAndGet(v -> v > 0 ? v - 1 : 0);
    }

    public static void beginBlockWrite(BlockPos pos) {
        if (pos != null) {
            beginSectionWrite(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        }
    }

    public static void endBlockWrite(BlockPos pos) {
        if (pos != null) {
            endSectionWrite(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        }
    }

    public static void beginSectionWrite(int sx, int sy, int sz) {
        long secKey = SectionPos.asLong(sx, sy, sz);
        int slot = mix64(secKey) & SECTION_CACHE_MASK;
        SECTION_EPOCHS.getAndUpdate(slot, v -> ((v + 0x100) & ~0xFF) | Math.min(0xFF, (v & 0xFF) + 1));
        WORLD_BLOCK_EPOCH.incrementAndGet();
    }

    public static void endSectionWrite(int sx, int sy, int sz) {
        long secKey = SectionPos.asLong(sx, sy, sz);
        int slot = mix64(secKey) & SECTION_CACHE_MASK;
        SECTION_EPOCHS.getAndUpdate(slot, v -> ((v + 0x100) & ~0xFF) | Math.max(0, (v & 0xFF) - 1));
        WORLD_BLOCK_EPOCH.incrementAndGet();
    }

    public static void beginChunkWrite(int cx, int cz, int minSectionY, int maxSectionY) {
        for (int sy = minSectionY; sy <= maxSectionY; sy++) {
            long secKey = SectionPos.asLong(cx, sy, cz);
            int slot = mix64(secKey) & SECTION_CACHE_MASK;
            SECTION_EPOCHS.getAndUpdate(slot, v -> ((v + 0x100) & ~0xFF) | Math.min(0xFF, (v & 0xFF) + 1));
        }
        WORLD_BLOCK_EPOCH.incrementAndGet();
    }

    public static void endChunkWrite(int cx, int cz, int minSectionY, int maxSectionY) {
        for (int sy = minSectionY; sy <= maxSectionY; sy++) {
            long secKey = SectionPos.asLong(cx, sy, cz);
            int slot = mix64(secKey) & SECTION_CACHE_MASK;
            SECTION_EPOCHS.getAndUpdate(slot, v -> ((v + 0x100) & ~0xFF) | Math.max(0, (v & 0xFF) - 1));
        }
        WORLD_BLOCK_EPOCH.incrementAndGet();
    }

    public static void invalidateBlock(BlockPos pos) {
        if (pos != null) {
            invalidateSection(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        }
    }

    public static void invalidateSection(int sx, int sy, int sz) {
        long secKey = SectionPos.asLong(sx, sy, sz);
        int slot = mix64(secKey) & SECTION_CACHE_MASK;
        SECTION_EPOCHS.getAndUpdate(slot, v -> ((v + 0x100) & ~0xFF) | (v & 0xFF));
        WORLD_BLOCK_EPOCH.incrementAndGet();
    }

    public static void invalidateChunk(int cx, int cz, int minSectionY, int maxSectionY) {
        for (int sy = minSectionY; sy <= maxSectionY; sy++) {
            long secKey = SectionPos.asLong(cx, sy, cz);
            int slot = mix64(secKey) & SECTION_CACHE_MASK;
            SECTION_EPOCHS.getAndUpdate(slot, v -> ((v + 0x100) & ~0xFF) | (v & 0xFF));
        }
        WORLD_BLOCK_EPOCH.incrementAndGet();
    }
    private static final AtomicReference<@Nullable RequestBatch> PENDING_REQ_SLOT = new AtomicReference<>(null);
    private static final AtomicReference<@Nullable RequestBatch> FREE_REQ_SLOT = new AtomicReference<>(new RequestBatch());
    private static final AtomicReference<@Nullable VisibilitySnapshot> READY_VIS_SLOT = new AtomicReference<>(null);
    private static final AtomicReference<@Nullable VisibilitySnapshot> FREE_VIS_SLOT = new AtomicReference<>(new VisibilitySnapshot());

    private static RequestBatch renderReq = new RequestBatch();
    private static VisibilitySnapshot renderVis = new VisibilitySnapshot();
    private static WeakReference<ClientLevel> renderLevelRef = new WeakReference<>(null);
    private static long lastSubmitNanos = 0L;
    private static final BlockPos.MutableBlockPos RENDER_WORK_POS = new BlockPos.MutableBlockPos();

    private static final int[] HYST_STAMPS = new int[HYST_TABLE_SIZE];
    private static final byte[] HYST_KINDS = new byte[HYST_TABLE_SIZE];
    private static final long[] HYST_KEYS = new long[HYST_TABLE_SIZE];
    private static final byte[] HYST_STREAKS = new byte[HYST_TABLE_SIZE];
    private static final int[] HYST_LAST_FRAME = new int[HYST_TABLE_SIZE];
    private static int hystTableStamp = 1;
    private static int hystFrame = 1;
    private static long lastHystFrameNanos = 0L;

    private static long frameCulledEntities = 0L;
    private static long frameCulledBlockEntities = 0L;
    private static long frameCulledParticles = 0L;
    private static volatile long lastFrameCulledEntities = 0L;
    private static volatile long lastFrameCulledBlockEntities = 0L;
    private static volatile long lastFrameCulledParticles = 0L;
    private static volatile long totalCulledEntities = 0L;
    private static volatile long totalCulledBlockEntities = 0L;
    private static volatile long totalCulledParticles = 0L;

    private static volatile @Nullable Thread workerThread;
    private static final Object WORKER_INIT_LOCK = new Object();

    private static final @Nullable MethodHandle IRIS_API_SHADOW_CHECK = resolveIrisApiShadowCheck();
    private static final @Nullable MethodHandle IRIS_RENDERER_ACTIVE_GETTER = resolveIrisShadowActiveGetter();
    private static final @Nullable MethodHandle IRIS_SHADOW_STATE_CHECK = resolveIrisShadowStateCheck();

    private static @Nullable MethodHandle resolveIrisApiShadowCheck() {
        try {
            ClassLoader cl = PapiOcclusionEngine.class.getClassLoader();
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, cl);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            MethodHandle getInstance = lookup.findStatic(apiClass, "getInstance", MethodType.methodType(apiClass));
            Object api = getInstance.invoke();
            if (api == null) {
                return null;
            }
            MethodHandle isShadow = lookup.findVirtual(apiClass, "isRenderingShadowPass", MethodType.methodType(boolean.class));
            return isShadow.bindTo(api);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable MethodHandle resolveIrisShadowActiveGetter() {
        String[] candidates = {
                "net.irisshaders.iris.shadows.ShadowRenderer",
                "net.coderbot.iris.pipeline.ShadowRenderer"
        };
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        ClassLoader cl = PapiOcclusionEngine.class.getClassLoader();
        for (String clsName : candidates) {
            try {
                Class<?> cls = Class.forName(clsName, false, cl);
                return lookup.findStaticGetter(cls, "ACTIVE", boolean.class);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static @Nullable MethodHandle resolveIrisShadowStateCheck() {
        String[] candidates = {
                "net.irisshaders.iris.shadows.ShadowRenderingState",
                "net.coderbot.iris.shadows.ShadowRenderingState"
        };
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        ClassLoader cl = PapiOcclusionEngine.class.getClassLoader();
        for (String clsName : candidates) {
            try {
                Class<?> cls = Class.forName(clsName, false, cl);
                return lookup.findStatic(cls, "areShadowsCurrentlyBeingRendered", MethodType.methodType(boolean.class));
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static boolean isShadowPass() {
        if (EXTERNAL_SHADOW_PASS_DEPTH.get() > 0) {
            return true;
        }
        MethodHandle apiMh = IRIS_API_SHADOW_CHECK;
        if (apiMh != null) {
            try {
                if ((boolean) apiMh.invokeExact()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        MethodHandle activeMh = IRIS_RENDERER_ACTIVE_GETTER;
        if (activeMh != null) {
            try {
                if ((boolean) activeMh.invokeExact()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        MethodHandle stateMh = IRIS_SHADOW_STATE_CHECK;
        if (stateMh != null) {
            try {
                return (boolean) stateMh.invokeExact();
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static boolean isShadowFrustum(@Nullable Frustum frustum) {
        if (frustum == null) {
            return true;
        }
        Class<?> cls = frustum.getClass();
        if (cls == Frustum.class) {
            return false;
        }
        String name = cls.getName();
        return name.startsWith("dev.vitrail.")
                || name.contains("Shadow")
                || name.contains("shadow")
                || name.startsWith("net.irisshaders.")
                || name.startsWith("net.coderbot.");
    }

    private PapiOcclusionEngine() {}

    private static void ensureWorkerStarted() {
        if (workerThread != null) {
            return;
        }
        synchronized (WORKER_INIT_LOCK) {
            if (workerThread != null) {
                return;
            }
            Thread t = new Thread(new PapiOcclusionWorker(), "PapiOcclusionWorker");
            t.setDaemon(true);
            t.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            workerThread = t;
            t.start();
        }
    }

    private static void resetHysteresis() {
        hystTableStamp++;
        if (hystTableStamp == 0) {
            java.util.Arrays.fill(HYST_STAMPS, 0);
            hystTableStamp = 1;
        }
        hystFrame = 1;
        lastHystFrameNanos = 0L;
    }

    private static void advanceFrameIfNeeded(long now) {
        if (now - lastHystFrameNanos >= HYST_FRAME_STEP_NANOS) {
            lastFrameCulledEntities = frameCulledEntities;
            lastFrameCulledBlockEntities = frameCulledBlockEntities;
            lastFrameCulledParticles = frameCulledParticles;
            frameCulledEntities = 0L;
            frameCulledBlockEntities = 0L;
            frameCulledParticles = 0L;
            hystFrame++;
            if (hystFrame == Integer.MAX_VALUE) {
                resetHysteresis();
            }
            lastHystFrameNanos = now;
        }
    }

    private static void recordCulled(byte targetKind) {
        if (targetKind == TARGET_ENTITY) {
            frameCulledEntities++;
            totalCulledEntities++;
        } else if (targetKind == TARGET_BLOCK_ENTITY) {
            frameCulledBlockEntities++;
            totalCulledBlockEntities++;
        } else if (targetKind == TARGET_PARTICLE) {
            frameCulledParticles++;
            totalCulledParticles++;
        }
    }

    public static void recordFrustumCulledParticle() {
        frameCulledParticles++;
        totalCulledParticles++;
    }

    public static long getLastFrameCulledEntities() {
        return lastFrameCulledEntities;
    }

    public static long getLastFrameCulledBlockEntities() {
        return lastFrameCulledBlockEntities;
    }

    public static long getLastFrameCulledParticles() {
        return lastFrameCulledParticles;
    }

    public static long getTotalCulledEntities() {
        return totalCulledEntities;
    }

    public static long getTotalCulledBlockEntities() {
        return totalCulledBlockEntities;
    }

    public static long getTotalCulledParticles() {
        return totalCulledParticles;
    }

    private static boolean applyHysteresis(byte targetKind, long targetKey, boolean rawOccluded) {
        int slot = mix64(targetKey ^ ((long) targetKind << 56)) & HYST_TABLE_MASK;
        int stamp = hystTableStamp;
        if (!rawOccluded) {
            for (int probe = 0; probe < 8; probe++) {
                int s = (slot + probe) & HYST_TABLE_MASK;
                if (HYST_STAMPS[s] == stamp && HYST_KINDS[s] == targetKind && HYST_KEYS[s] == targetKey) {
                    HYST_STREAKS[s] = 0;
                    HYST_LAST_FRAME[s] = 0;
                    break;
                }
            }
            return false;
        }
        int frame = hystFrame;
        int found = -1;
        int freeSlot = -1;
        for (int probe = 0; probe < 8; probe++) {
            int s = (slot + probe) & HYST_TABLE_MASK;
            if (HYST_STAMPS[s] == stamp && HYST_KINDS[s] == targetKind && HYST_KEYS[s] == targetKey) {
                found = s;
                break;
            }
            if (freeSlot < 0) {
                if (HYST_STAMPS[s] != stamp
                        || HYST_STREAKS[s] == 0
                        || (HYST_LAST_FRAME[s] != frame && HYST_LAST_FRAME[s] != frame - 1)) {
                    freeSlot = s;
                }
            }
        }
        if (found < 0) {
            if (freeSlot < 0) {
                return false;
            }
            HYST_STAMPS[freeSlot] = stamp;
            HYST_KINDS[freeSlot] = targetKind;
            HYST_KEYS[freeSlot] = targetKey;
            HYST_STREAKS[freeSlot] = 1;
            HYST_LAST_FRAME[freeSlot] = frame;
            return false;
        }
        int last = HYST_LAST_FRAME[found];
        byte streak = HYST_STREAKS[found];
        if (last != frame) {
            if (last == frame - 1) {
                streak = (byte) Math.min(4, streak + 1);
            } else {
                streak = 1;
            }
            HYST_STREAKS[found] = streak;
            HYST_LAST_FRAME[found] = frame;
        }
        if (streak >= HYST_HOLD_FRAMES) {
            recordCulled(targetKind);
            return true;
        }
        return false;
    }

    public static void clear() {
        int newGen = GENERATION.incrementAndGet();
        WORLD_BLOCK_EPOCH.incrementAndGet();
        EXTERNAL_SHADOW_PASS_DEPTH.set(0);
        if (RenderSystem.isOnRenderThread()) {
            renderLevelRef = new WeakReference<>(null);
            renderVis.invalidate();
            renderReq.reset(newGen, null, 0L, 0.0, 0.0, 0.0);
            lastSubmitNanos = 0L;
            resetHysteresis();
            frameCulledEntities = 0L;
            frameCulledBlockEntities = 0L;
            frameCulledParticles = 0L;
        }
        RequestBatch pending = PENDING_REQ_SLOT.getAndSet(null);
        if (pending != null) {
            pending.levelRef = new WeakReference<>(null);
            FREE_REQ_SLOT.set(pending);
        }
        VisibilitySnapshot ready = READY_VIS_SLOT.getAndSet(null);
        if (ready != null) {
            ready.invalidate();
            FREE_VIS_SLOT.set(ready);
        }
        lastFrameCulledEntities = 0L;
        lastFrameCulledBlockEntities = 0L;
        lastFrameCulledParticles = 0L;
        totalCulledEntities = 0L;
        totalCulledBlockEntities = 0L;
        totalCulledParticles = 0L;
        Thread wt = workerThread;
        if (wt != null) {
            LockSupport.unpark(wt);
        }
    }

    public static boolean isParticleOccluded(ClientLevel level, Vec3 cameraPos, double px, double py, double pz) {
        if (!RenderSystem.isOnRenderThread() || isShadowPass()) {
            return false;
        }
        int bx = Mth.floor(px);
        int by = Mth.floor(py);
        int bz = Mth.floor(pz);
        return isBoxOccluded(
                level,
                cameraPos,
                (double) bx,
                (double) by,
                (double) bz,
                (double) (bx + 1),
                (double) (by + 1),
                (double) (bz + 1),
                TARGET_PARTICLE,
                BlockPos.asLong(bx, by, bz),
                PARTICLE_MIN_CULL_DIST_SQR
        );
    }

    public static boolean isBoxOccluded(
            ClientLevel level,
            Vec3 cameraPos,
            AABB box,
            byte targetKind,
            long targetKey,
            double minCullDistSqr
    ) {
        return isBoxOccluded(
                level,
                cameraPos,
                box.minX,
                box.minY,
                box.minZ,
                box.maxX,
                box.maxY,
                box.maxZ,
                targetKind,
                targetKey,
                minCullDistSqr
        );
    }

    public static boolean isBoxOccluded(
            ClientLevel level,
            Vec3 cameraPos,
            double rawMinX,
            double rawMinY,
            double rawMinZ,
            double rawMaxX,
            double rawMaxY,
            double rawMaxZ,
            byte targetKind,
            long targetKey,
            double minCullDistSqr
    ) {
        if (!RenderSystem.isOnRenderThread() || isShadowPass()) {
            return false;
        }

        double cx = cameraPos.x;
        double cy = cameraPos.y;
        double cz = cameraPos.z;

        double bxMin = rawMinX - 0.05;
        double byMin = rawMinY - 0.05;
        double bzMin = rawMinZ - 0.05;
        double bxMax = rawMaxX + 0.05;
        double byMax = rawMaxY + 0.05;
        double bzMax = rawMaxZ + 0.05;

        if (bxMax - bxMin > 6.5 || byMax - byMin > 6.5 || bzMax - bzMin > 6.5) {
            return false;
        }

        if (cx >= bxMin && cx <= bxMax && cy >= byMin && cy <= byMax && cz >= bzMin && cz <= bzMax) {
            return applyHysteresis(targetKind, targetKey, false);
        }

        double centerX = 0.5 * (bxMin + bxMax);
        double centerY = 0.5 * (byMin + byMax);
        double centerZ = 0.5 * (bzMin + bzMax);
        double dxC = centerX - cx;
        double dyC = centerY - cy;
        double dzC = centerZ - cz;
        if (dxC * dxC + dyC * dyC + dzC * dzC <= minCullDistSqr) {
            return applyHysteresis(targetKind, targetKey, false);
        }

        int camBlkX = Mth.floor(cx);
        int camBlkY = Mth.floor(cy);
        int camBlkZ = Mth.floor(cz);
        if (isLiveOpaque(level, RENDER_WORK_POS.set(camBlkX, camBlkY, camBlkZ))) {
            return applyHysteresis(targetKind, targetKey, false);
        }

        ensureWorkerStarted();

        long gameTime = level.getGameTime();
        int gen = GENERATION.get();
        if (renderLevelRef.get() != level) {
            gen = GENERATION.incrementAndGet();
            renderLevelRef = new WeakReference<>(level);
            renderVis.invalidate();
            resetHysteresis();
            renderReq.reset(gen, level, gameTime, cx, cy, cz);
            lastSubmitNanos = System.nanoTime();
            Thread wt = workerThread;
            if (wt != null) {
                LockSupport.unpark(wt);
            }
        }

        VisibilitySnapshot nextVis = READY_VIS_SLOT.getAndSet(null);
        if (nextVis != null) {
            FREE_VIS_SLOT.set(renderVis);
            renderVis = nextVis;
        }

        long now = System.nanoTime();
        advanceFrameIfNeeded(now);

        if (renderReq.generation != gen) {
            renderReq.reset(gen, level, gameTime, cx, cy, cz);
            lastSubmitNanos = now;
        } else if (renderReq.count > 0 && (now - lastSubmitNanos >= SUBMIT_INTERVAL_NANOS || renderReq.count >= MAX_BATCH_REQUESTS)) {
            renderReq.gameTime = gameTime;
            renderReq.camX = cx;
            renderReq.camY = cy;
            renderReq.camZ = cz;
            RequestBatch displacedReq = PENDING_REQ_SLOT.getAndSet(renderReq);
            RequestBatch nextReq = displacedReq != null ? displacedReq : FREE_REQ_SLOT.getAndSet(null);
            renderReq = nextReq != null ? nextReq : new RequestBatch();
            Thread wt = workerThread;
            if (wt != null) {
                LockSupport.unpark(wt);
            }
            renderReq.reset(gen, level, gameTime, cx, cy, cz);
            lastSubmitNanos = now;
        }

        renderReq.add(targetKind, targetKey, bxMin, byMin, bzMin, bxMax, byMax, bzMax);

        VisibilitySnapshot vis = renderVis;
        if (vis.generation != gen) {
            return applyHysteresis(targetKind, targetKey, false);
        }
        long age = gameTime - vis.gameTime;
        if (age < 0L || age > SINGLE_VOXEL_MAX_AGE_TICKS) {
            return applyHysteresis(targetKind, targetKey, false);
        }

        int slot = vis.findSlot(targetKind, targetKey);
        if (slot < 0) {
            return applyHysteresis(targetKind, targetKey, false);
        }
        byte state = vis.states[slot];
        if (state == STATE_VISIBLE) {
            return applyHysteresis(targetKind, targetKey, false);
        }

        long packed = vis.blockerPos[slot];
        int ox = BlockPos.getX(packed);
        int oy = BlockPos.getY(packed);
        int oz = BlockPos.getZ(packed);
        if (ox == camBlkX && oy == camBlkY && oz == camBlkZ) {
            return applyHysteresis(targetKind, targetKey, false);
        }
        if (!isLiveOpaque(level, RENDER_WORK_POS.set(ox, oy, oz))) {
            return applyHysteresis(targetKind, targetKey, false);
        }

        boolean rawOccluded;
        if (state == STATE_OCCLUDED_SINGLE_VOXEL) {
            rawOccluded = allCornersBlockedByVoxel(cx, cy, cz, bxMin, byMin, bzMin, bxMax, byMax, bzMax, ox, oy, oz);
        } else if (age > MULTI_VOXEL_MAX_AGE_TICKS || vis.blockEpoch != WORLD_BLOCK_EPOCH.get()) {
            rawOccluded = false;
        } else {
            double dCamX = cx - vis.camX;
            double dCamY = cy - vis.camY;
            double dCamZ = cz - vis.camZ;
            if (dCamX * dCamX + dCamY * dCamY + dCamZ * dCamZ > MULTI_VOXEL_MAX_SHIFT_SQR) {
                rawOccluded = false;
            } else {
                double dBoxX = centerX - vis.centerX[slot];
                double dBoxY = centerY - vis.centerY[slot];
                double dBoxZ = centerZ - vis.centerZ[slot];
                if (dBoxX * dBoxX + dBoxY * dBoxY + dBoxZ * dBoxZ > MULTI_VOXEL_MAX_SHIFT_SQR) {
                    rawOccluded = false;
                } else {
                    rawOccluded = true;
                    int extraCount = vis.cornerBlockerCount[slot] & 0xFF;
                    int base = slot << 3;
                    for (int k = 0; k < extraCount; k++) {
                        long cb = vis.cornerBlockers[base + k];
                        int cbx = BlockPos.getX(cb);
                        int cby = BlockPos.getY(cb);
                        int cbz = BlockPos.getZ(cb);
                        if ((cbx == camBlkX && cby == camBlkY && cbz == camBlkZ)
                                || !isLiveOpaque(level, RENDER_WORK_POS.set(cbx, cby, cbz))) {
                            rawOccluded = false;
                            break;
                        }
                    }
                }
            }
        }
        return applyHysteresis(targetKind, targetKey, rawOccluded);
    }

    private static final class PapiOcclusionWorker implements Runnable {

        private final SectionEntry[] sectionCache = new SectionEntry[SECTION_CACHE_SIZE];
        private final long[] cornerHitBuf = new long[8];
        private RequestBatch workerReq = new RequestBatch();
        private VisibilitySnapshot workerVis = new VisibilitySnapshot();
        private int workerGeneration = -1;

        PapiOcclusionWorker() {
            for (int i = 0; i < SECTION_CACHE_SIZE; i++) {
                this.sectionCache[i] = new SectionEntry();
            }
        }

        private void clearSections() {
            for (int i = 0; i < SECTION_CACHE_SIZE; i++) {
                SectionEntry e = this.sectionCache[i];
                e.secKey = Long.MIN_VALUE;
                e.observedEpoch = 0;
                e.expireTick = Long.MIN_VALUE;
                e.kind = KIND_EMPTY;
                e.words = EMPTY_WORDS;
            }
        }

        @Override
        public void run() {
            while (true) {
                try {
                    int currentGen = GENERATION.get();
                    if (this.workerGeneration != currentGen) {
                        clearSections();
                        this.workerGeneration = currentGen;
                        this.workerReq.levelRef = new WeakReference<>(null);
                        this.workerVis.invalidate();
                    }

                    RequestBatch nextReq = PENDING_REQ_SLOT.getAndSet(null);
                    if (nextReq == null) {
                        LockSupport.park();
                        continue;
                    }

                    this.workerReq = nextReq;
                    processBatch(nextReq);
                    nextReq.levelRef = new WeakReference<>(null);
                    FREE_REQ_SLOT.set(nextReq);
                } catch (Throwable ignored) {
                }
            }
        }

        private void processBatch(RequestBatch req) {
            int reqGen = req.generation;
            if (reqGen != GENERATION.get()) {
                return;
            }
            ClientLevel level = req.levelRef.get();
            int count = req.count;
            if (level == null || count <= 0) {
                return;
            }

            long gameTime = req.gameTime;
            double cx = req.camX;
            double cy = req.camY;
            double cz = req.camZ;
            int batchBlockEpoch = WORLD_BLOCK_EPOCH.get();
            this.workerVis.reset(reqGen, gameTime, batchBlockEpoch, cx, cy, cz);

            for (int i = 0; i < count; i++) {
                if ((i & 31) == 0 && GENERATION.get() != reqGen) {
                    return;
                }
                double bxMin = req.minX[i];
                double byMin = req.minY[i];
                double bzMin = req.minZ[i];
                double bxMax = req.maxX[i];
                double byMax = req.maxY[i];
                double bzMax = req.maxZ[i];

                double centerX = 0.5 * (bxMin + bxMax);
                double centerY = 0.5 * (byMin + byMax);
                double centerZ = 0.5 * (bzMin + bzMax);

                long firstHit = traceRay(level, gameTime, cx, cy, cz, centerX, centerY, centerZ);
                if (firstHit == Long.MIN_VALUE) {
                    this.workerVis.put(req.kinds[i], req.keys[i], STATE_VISIBLE, 0L, (float) centerX, (float) centerY, (float) centerZ);
                    continue;
                }

                int fx = BlockPos.getX(firstHit);
                int fy = BlockPos.getY(firstHit);
                int fz = BlockPos.getZ(firstHit);
                if (allCornersBlockedByVoxel(cx, cy, cz, bxMin, byMin, bzMin, bxMax, byMax, bzMax, fx, fy, fz)) {
                    this.workerVis.put(req.kinds[i], req.keys[i], STATE_OCCLUDED_SINGLE_VOXEL, firstHit, (float) centerX, (float) centerY, (float) centerZ);
                    continue;
                }

                boolean allBlocked = true;
                int extraCount = 0;
                for (int mask = 0; mask < 8; mask++) {
                    double tx = (mask & 1) == 0 ? bxMin : bxMax;
                    double ty = (mask & 2) == 0 ? byMin : byMax;
                    double tz = (mask & 4) == 0 ? bzMin : bzMax;
                    long hit = traceRay(level, gameTime, cx, cy, cz, tx, ty, tz);
                    if (hit == Long.MIN_VALUE) {
                        allBlocked = false;
                        break;
                    }
                    if (hit != firstHit) {
                        boolean dup = false;
                        for (int k = 0; k < extraCount; k++) {
                            if (this.cornerHitBuf[k] == hit) {
                                dup = true;
                                break;
                            }
                        }
                        if (!dup && extraCount < 8) {
                            this.cornerHitBuf[extraCount++] = hit;
                        }
                    }
                }
                if (allBlocked) {
                    this.workerVis.put(req.kinds[i], req.keys[i], STATE_OCCLUDED_MULTI_VOXEL, firstHit, this.cornerHitBuf, extraCount, (float) centerX, (float) centerY, (float) centerZ);
                } else {
                    this.workerVis.put(req.kinds[i], req.keys[i], STATE_VISIBLE, firstHit, (float) centerX, (float) centerY, (float) centerZ);
                }
            }

            req.levelRef = new WeakReference<>(null);
            if (GENERATION.get() == reqGen) {
                VisibilitySnapshot displacedVis = READY_VIS_SLOT.getAndSet(this.workerVis);
                VisibilitySnapshot freeVis = displacedVis != null ? displacedVis : FREE_VIS_SLOT.getAndSet(null);
                this.workerVis = freeVis != null ? freeVis : new VisibilitySnapshot();
            }
        }

        private long traceRay(
                ClientLevel level,
                long gameTime,
                double ox, double oy, double oz,
                double tx, double ty, double tz
        ) {
            double dx = tx - ox;
            double dy = ty - oy;
            double dz = tz - oz;

            int x = Mth.floor(ox);
            int y = Mth.floor(oy);
            int z = Mth.floor(oz);
            int endX = Mth.floor(tx);
            int endY = Mth.floor(ty);
            int endZ = Mth.floor(tz);

            if (x == endX && y == endY && z == endZ) {
                return Long.MIN_VALUE;
            }

            int stepX = dx > 0.0 ? 1 : (dx < 0.0 ? -1 : 0);
            int stepY = dy > 0.0 ? 1 : (dy < 0.0 ? -1 : 0);
            int stepZ = dz > 0.0 ? 1 : (dz < 0.0 ? -1 : 0);

            double tDeltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.MAX_VALUE;
            double tDeltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.MAX_VALUE;
            double tDeltaZ = stepZ != 0 ? Math.abs(1.0 / dz) : Double.MAX_VALUE;

            double tMaxX = stepX != 0 ? ((stepX > 0 ? (x + 1.0 - ox) : (ox - x)) * tDeltaX) : Double.MAX_VALUE;
            double tMaxY = stepY != 0 ? ((stepY > 0 ? (y + 1.0 - oy) : (oy - y)) * tDeltaY) : Double.MAX_VALUE;
            double tMaxZ = stepZ != 0 ? ((stepZ > 0 ? (z + 1.0 - oz) : (oz - z)) * tDeltaZ) : Double.MAX_VALUE;

            int cachedSecX = Integer.MIN_VALUE;
            int cachedSecY = Integer.MIN_VALUE;
            int cachedSecZ = Integer.MIN_VALUE;
            SectionEntry secEntry = null;

            for (int iter = 0; iter < 192; iter++) {
                if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                    if (tMaxX >= 1.0) return Long.MIN_VALUE;
                    x += stepX;
                    tMaxX += tDeltaX;
                } else if (tMaxY <= tMaxZ) {
                    if (tMaxY >= 1.0) return Long.MIN_VALUE;
                    y += stepY;
                    tMaxY += tDeltaY;
                } else {
                    if (tMaxZ >= 1.0) return Long.MIN_VALUE;
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }

                if (x == endX && y == endY && z == endZ) {
                    return Long.MIN_VALUE;
                }

                int sx = x >> 4;
                int sy = y >> 4;
                int sz = z >> 4;
                if (sx != cachedSecX || sy != cachedSecY || sz != cachedSecZ) {
                    cachedSecX = sx;
                    cachedSecY = sy;
                    cachedSecZ = sz;
                    secEntry = getOrBuildSection(level, gameTime, sx, sy, sz);
                }

                if (secEntry.kind == KIND_EMPTY) {
                    continue;
                }

                if (secEntry.kind == KIND_FULL) {
                    return BlockPos.asLong(x, y, z);
                }

                int lx = x & 15;
                int ly = y & 15;
                int lz = z & 15;
                int wordIdx = (ly << 2) | (lz >> 2);
                int bit = ((lz & 3) << 4) | lx;
                if (((secEntry.words[wordIdx] >>> bit) & 1L) != 0L) {
                    return BlockPos.asLong(x, y, z);
                }
            }
            return Long.MIN_VALUE;
        }

        @SuppressWarnings("unchecked")
        private SectionEntry getOrBuildSection(ClientLevel level, long gameTime, int sx, int sy, int sz) {
            long secKey = SectionPos.asLong(sx, sy, sz);
            int slot = mix64(secKey) & SECTION_CACHE_MASK;
            SectionEntry entry = this.sectionCache[slot];
            int curEpoch = SECTION_EPOCHS.get(slot);
            if ((curEpoch & 0xFF) != 0) {
                entry.secKey = Long.MIN_VALUE;
                entry.expireTick = Long.MIN_VALUE;
                entry.kind = KIND_EMPTY;
                entry.words = EMPTY_WORDS;
                return entry;
            }
            if (entry.secKey == secKey && entry.observedEpoch == curEpoch && gameTime <= entry.expireTick) {
                return entry;
            }

            LevelChunk chunk;
            try {
                chunk = level.getChunkSource().getChunk(sx, sz, false);
            } catch (Throwable ignored) {
                entry.secKey = Long.MIN_VALUE;
                entry.expireTick = Long.MIN_VALUE;
                entry.kind = KIND_EMPTY;
                entry.words = EMPTY_WORDS;
                return entry;
            }
            if (chunk == null) {
                entry.secKey = Long.MIN_VALUE;
                entry.expireTick = Long.MIN_VALUE;
                entry.kind = KIND_EMPTY;
                entry.words = EMPTY_WORDS;
                return entry;
            }

            int secIndex = chunk.getSectionIndexFromSectionY(sy);
            LevelChunkSection[] sections = chunk.getSections();
            if (secIndex < 0 || secIndex >= sections.length) {
                if (SECTION_EPOCHS.get(slot) != curEpoch) {
                    entry.secKey = Long.MIN_VALUE;
                    entry.expireTick = Long.MIN_VALUE;
                    entry.kind = KIND_EMPTY;
                    entry.words = EMPTY_WORDS;
                    return entry;
                }
                entry.secKey = secKey;
                entry.observedEpoch = curEpoch;
                entry.expireTick = gameTime + SECTION_TTL_TICKS;
                entry.kind = KIND_EMPTY;
                entry.words = EMPTY_WORDS;
                return entry;
            }

            LevelChunkSection section = sections[secIndex];
            if (section == null || section.hasOnlyAir()) {
                if (SECTION_EPOCHS.get(slot) != curEpoch) {
                    entry.secKey = Long.MIN_VALUE;
                    entry.expireTick = Long.MIN_VALUE;
                    entry.kind = KIND_EMPTY;
                    entry.words = EMPTY_WORDS;
                    return entry;
                }
                entry.secKey = secKey;
                entry.observedEpoch = curEpoch;
                entry.expireTick = gameTime + SECTION_TTL_TICKS;
                entry.kind = KIND_EMPTY;
                entry.words = EMPTY_WORDS;
                return entry;
            }

            PalettedContainer<BlockState> states = section.getStates();
            VarHandle dataHandle = PALETTED_DATA_HANDLE;
            if (states == null || dataHandle == null) {
                entry.secKey = Long.MIN_VALUE;
                entry.expireTick = Long.MIN_VALUE;
                entry.kind = KIND_EMPTY;
                entry.words = EMPTY_WORDS;
                return entry;
            }

            Object rawData = dataHandle.getVolatile(states);
            if (!(rawData instanceof PalettedContainerDataAccessor<?> rawAccessor)) {
                entry.secKey = Long.MIN_VALUE;
                entry.expireTick = Long.MIN_VALUE;
                entry.kind = KIND_EMPTY;
                entry.words = EMPTY_WORDS;
                return entry;
            }

            PalettedContainerDataAccessor<BlockState> snap = (PalettedContainerDataAccessor<BlockState>) rawAccessor;
            Palette<BlockState> palette = snap.papi$getPalette();
            BitStorage storage = snap.papi$getStorage();
            if (palette == null || storage == null) {
                entry.secKey = Long.MIN_VALUE;
                entry.expireTick = Long.MIN_VALUE;
                entry.kind = KIND_EMPTY;
                entry.words = EMPTY_WORDS;
                return entry;
            }

            try {
                int paletteSize = palette.getSize();
                if (paletteSize <= 0) {
                    entry.secKey = Long.MIN_VALUE;
                    entry.expireTick = Long.MIN_VALUE;
                    entry.kind = KIND_EMPTY;
                    entry.words = EMPTY_WORDS;
                    return entry;
                }
                if (paletteSize == 1) {
                    BlockState only = palette.valueFor(0);
                    boolean solid = only != null && !only.isAir() && only.canOcclude() && only.isSolidRender();
                    if (dataHandle.getVolatile(states) != rawData || SECTION_EPOCHS.get(slot) != curEpoch) {
                        entry.secKey = Long.MIN_VALUE;
                        entry.expireTick = Long.MIN_VALUE;
                        entry.kind = KIND_EMPTY;
                        entry.words = EMPTY_WORDS;
                        return entry;
                    }
                    entry.secKey = secKey;
                    entry.observedEpoch = curEpoch;
                    entry.expireTick = gameTime + SECTION_TTL_TICKS;
                    entry.kind = solid ? KIND_FULL : KIND_EMPTY;
                    entry.words = EMPTY_WORDS;
                    return entry;
                }

                int bits = storage.getBits();
                boolean[] opaqueTable = null;
                int tableLimit = 0;
                if (bits > 0 && bits <= 8) {
                    int cap = 1 << bits;
                    opaqueTable = new boolean[cap];
                    tableLimit = Math.min(paletteSize, cap);
                    for (int id = 0; id < tableLimit; id++) {
                        BlockState st = palette.valueFor(id);
                        opaqueTable[id] = st != null && !st.isAir() && st.canOcclude() && st.isSolidRender();
                    }
                }

                long[] words = (entry.words.length == 64) ? entry.words : new long[64];
                int solidCount = 0;
                boolean raced = false;
                for (int y = 0; y < 16; y++) {
                    int yBase = y << 8;
                    for (int z = 0; z < 16; z += 4) {
                        long word = 0L;
                        for (int dz = 0; dz < 4; dz++) {
                            int gz = z + dz;
                            int yzBase = yBase | (gz << 4);
                            int shiftBase = dz << 4;
                            for (int x = 0; x < 16; x++) {
                                int id = storage.get(yzBase | x);
                                boolean isOpaque;
                                if (opaqueTable != null) {
                                    if (id >= 0 && id < tableLimit) {
                                        isOpaque = opaqueTable[id];
                                    } else {
                                        raced = true;
                                        isOpaque = false;
                                    }
                                } else {
                                    BlockState st = palette.valueFor(id);
                                    isOpaque = st != null && !st.isAir() && st.canOcclude() && st.isSolidRender();
                                }
                                if (isOpaque) {
                                    word |= (1L << (shiftBase | x));
                                    solidCount++;
                                }
                            }
                        }
                        words[(y << 2) | (z >> 2)] = word;
                    }
                }

                if (raced || dataHandle.getVolatile(states) != rawData || SECTION_EPOCHS.get(slot) != curEpoch) {
                    entry.secKey = Long.MIN_VALUE;
                    entry.expireTick = Long.MIN_VALUE;
                    entry.kind = KIND_EMPTY;
                    entry.words = EMPTY_WORDS;
                    return entry;
                }

                entry.secKey = secKey;
                entry.observedEpoch = curEpoch;
                entry.expireTick = gameTime + SECTION_TTL_TICKS;
                if (solidCount == 0) {
                    entry.kind = KIND_EMPTY;
                    entry.words = EMPTY_WORDS;
                } else if (solidCount == 4096) {
                    entry.kind = KIND_FULL;
                    entry.words = EMPTY_WORDS;
                } else {
                    entry.kind = KIND_SPARSE;
                    entry.words = words;
                }
                return entry;
            } catch (Throwable ignored) {
                entry.secKey = Long.MIN_VALUE;
                entry.expireTick = Long.MIN_VALUE;
                entry.kind = KIND_EMPTY;
                entry.words = EMPTY_WORDS;
                return entry;
            }
        }
    }

    private static boolean allCornersBlockedByVoxel(
            double cx, double cy, double cz,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            int ox, int oy, int oz
    ) {
        double vMinX = ox;
        double vMinY = oy;
        double vMinZ = oz;
        double vMaxX = ox + 1.0;
        double vMaxY = oy + 1.0;
        double vMaxZ = oz + 1.0;
        for (int mask = 0; mask < 8; mask++) {
            double tx = (mask & 1) == 0 ? minX : maxX;
            double ty = (mask & 2) == 0 ? minY : maxY;
            double tz = (mask & 4) == 0 ? minZ : maxZ;
            if (!segmentHitsUnitBox(cx, cy, cz, tx, ty, tz, vMinX, vMinY, vMinZ, vMaxX, vMaxY, vMaxZ)) {
                return false;
            }
        }
        return true;
    }

    private static boolean segmentHitsUnitBox(
            double cx, double cy, double cz,
            double tx, double ty, double tz,
            double bMinX, double bMinY, double bMinZ,
            double bMaxX, double bMaxY, double bMaxZ
    ) {
        double dx = tx - cx;
        double dy = ty - cy;
        double dz = tz - cz;
        double tMin = 0.0;
        double tMax = 1.0;

        if (Math.abs(dx) < 1.0E-9) {
            if (cx < bMinX || cx > bMaxX) return false;
        } else {
            double inv = 1.0 / dx;
            double t1 = (bMinX - cx) * inv;
            double t2 = (bMaxX - cx) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }

        if (Math.abs(dy) < 1.0E-9) {
            if (cy < bMinY || cy > bMaxY) return false;
        } else {
            double inv = 1.0 / dy;
            double t1 = (bMinY - cy) * inv;
            double t2 = (bMaxY - cy) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }

        if (Math.abs(dz) < 1.0E-9) {
            if (cz < bMinZ || cz > bMaxZ) return false;
        } else {
            double inv = 1.0 / dz;
            double t1 = (bMinZ - cz) * inv;
            double t2 = (bMaxZ - cz) * inv;
            if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }

        return tMax > 0.0 && tMin < 1.0;
    }

    private static boolean isLiveOpaque(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.canOcclude() && state.isSolidRender();
    }

    private static int mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return (int) (z ^ (z >>> 31));
    }
}
