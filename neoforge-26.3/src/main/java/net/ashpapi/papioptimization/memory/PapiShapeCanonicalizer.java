package net.ashpapi.papioptimization.memory;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.ashpapi.papioptimization.mixin.physics.VoxelShapeAccessor;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class PapiShapeCanonicalizer {

    private static final Int2ObjectOpenHashMap<boolean[]> STURDY_INTERNER = new Int2ObjectOpenHashMap<>(64);
    private static final Map<ShapeKey, VoxelShape> SHAPE_INTERNER = new HashMap<>(1024);
    private static final Map<FaceArrayKey, VoxelShape[]> FACE_ARRAY_INTERNER = new HashMap<>(512);

    private PapiShapeCanonicalizer() {}

    public static boolean[] internFaceSturdy(boolean[] raw) {
        if (raw == null || raw.length > 31) {
            return raw;
        }
        int mask = 0;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i]) {
                mask |= (1 << i);
            }
        }
        int key = (raw.length << 24) | mask;
        synchronized (STURDY_INTERNER) {
            boolean[] existing = STURDY_INTERNER.get(key);
            if (existing != null) {
                return existing;
            }
            STURDY_INTERNER.put(key, raw);
            return raw;
        }
    }

    public static VoxelShape internShape(VoxelShape shape) {
        if (shape == null
                || shape == Shapes.empty()
                || shape == Shapes.block()
                || !shape.getClass().getName().startsWith("net.minecraft.")) {
            return shape;
        }
        ShapeKey key = new ShapeKey(shape);
        synchronized (SHAPE_INTERNER) {
            VoxelShape existing = SHAPE_INTERNER.get(key);
            if (existing != null) {
                return existing;
            }
            SHAPE_INTERNER.put(key, shape);
            return shape;
        }
    }

    public static VoxelShape[] internFaceArray(VoxelShape[] faces) {
        if (faces == null || faces.length != 6) {
            return faces;
        }
        for (int i = 0; i < 6; i++) {
            VoxelShape canon = internShape(faces[i]);
            if (canon != faces[i]) {
                faces[i] = canon;
            }
        }
        FaceArrayKey key = new FaceArrayKey(faces);
        synchronized (FACE_ARRAY_INTERNER) {
            VoxelShape[] existing = FACE_ARRAY_INTERNER.get(key);
            if (existing != null) {
                return existing;
            }
            FACE_ARRAY_INTERNER.put(key, faces);
            return faces;
        }
    }

    private static final class ShapeKey {
        final VoxelShape shape;
        final int hash;

        ShapeKey(VoxelShape shape) {
            this.shape = shape;
            DiscreteVoxelShape d = ((VoxelShapeAccessor) shape).papi$getDiscreteShape();
            int h = shape.getClass().hashCode();
            h = 31 * h + d.getXSize();
            h = 31 * h + d.getYSize();
            h = 31 * h + d.getZSize();
            h = 31 * h + Double.hashCode(d.getXSize() > 0 ? shape.min(Direction.Axis.X) : 0.0);
            h = 31 * h + Double.hashCode(d.getXSize() > 0 ? shape.max(Direction.Axis.X) : 0.0);
            h = 31 * h + shape.getCoords(Direction.Axis.X).hashCode();
            h = 31 * h + shape.getCoords(Direction.Axis.Y).hashCode();
            h = 31 * h + shape.getCoords(Direction.Axis.Z).hashCode();
            int sx = d.getXSize();
            int sy = d.getYSize();
            int sz = d.getZSize();
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        h = 31 * h + (d.isFull(x, y, z) ? 1231 : 1237);
                    }
                }
            }
            this.hash = h;
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ShapeKey other) || this.hash != other.hash) return false;
            if (this.shape == other.shape) return true;
            if (this.shape.getClass() != other.shape.getClass()) return false;
            DiscreteVoxelShape d1 = ((VoxelShapeAccessor) this.shape).papi$getDiscreteShape();
            DiscreteVoxelShape d2 = ((VoxelShapeAccessor) other.shape).papi$getDiscreteShape();
            int sx = d1.getXSize();
            int sy = d1.getYSize();
            int sz = d1.getZSize();
            if (sx != d2.getXSize() || sy != d2.getYSize() || sz != d2.getZSize()) return false;
            DoubleList x1 = this.shape.getCoords(Direction.Axis.X);
            DoubleList x2 = other.shape.getCoords(Direction.Axis.X);
            DoubleList y1 = this.shape.getCoords(Direction.Axis.Y);
            DoubleList y2 = other.shape.getCoords(Direction.Axis.Y);
            DoubleList z1 = this.shape.getCoords(Direction.Axis.Z);
            DoubleList z2 = other.shape.getCoords(Direction.Axis.Z);
            if (!x1.equals(x2) || !y1.equals(y2) || !z1.equals(z2)) return false;
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    for (int z = 0; z < sz; z++) {
                        if (d1.isFull(x, y, z) != d2.isFull(x, y, z)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }
    }

    private static final class FaceArrayKey {
        final VoxelShape[] faces;
        final int hash;

        FaceArrayKey(VoxelShape[] faces) {
            this.faces = faces;
            int h = 1;
            for (int i = 0; i < 6; i++) {
                h = 31 * h + System.identityHashCode(faces[i]);
            }
            this.hash = h;
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof FaceArrayKey other) || this.hash != other.hash) return false;
            for (int i = 0; i < 6; i++) {
                if (this.faces[i] != other.faces[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
