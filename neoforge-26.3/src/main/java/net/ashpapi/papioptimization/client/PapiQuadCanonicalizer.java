package net.ashpapi.papioptimization.client;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import net.neoforged.neoforge.client.model.quad.BakedNormals;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@OnlyIn(Dist.CLIENT)
public final class PapiQuadCanonicalizer {

    private static final int MAX_VEC_ENTRIES = 131072;
    private static final int MAX_META_ENTRIES = 65536;
    private static final int MAX_QUAD_ENTRIES = 262144;

    private static final ConcurrentHashMap<Vector3fc, Vector3fc> VEC_POOL = new ConcurrentHashMap<>(4096);
    private static final ConcurrentHashMap<BakedQuad.MaterialInfo, BakedQuad.MaterialInfo> MATERIAL_POOL = new ConcurrentHashMap<>(2048);
    private static final ConcurrentHashMap<BakedNormals, BakedNormals> NORMALS_POOL = new ConcurrentHashMap<>(256);
    private static final ConcurrentHashMap<BakedColors, BakedColors> COLORS_POOL = new ConcurrentHashMap<>(256);
    private static final ConcurrentHashMap<BakedQuad, BakedQuad> QUAD_POOL = new ConcurrentHashMap<>(8192);

    private PapiQuadCanonicalizer() {}

    public static BakedQuad canonicalize(BakedQuad quad) {
        if (quad == null) {
            return null;
        }
        BakedQuad existing = QUAD_POOL.get(quad);
        if (existing != null) {
            return existing;
        }

        Vector3fc p0 = internVec(quad.position0());
        Vector3fc p1 = internVec(quad.position1());
        Vector3fc p2 = internVec(quad.position2());
        Vector3fc p3 = internVec(quad.position3());
        BakedQuad.MaterialInfo mat = internMaterial(quad.materialInfo());
        BakedNormals normals = internNormals(quad.bakedNormals());
        BakedColors colors = internColors(quad.bakedColors());

        BakedQuad target = (p0 == quad.position0()
                && p1 == quad.position1()
                && p2 == quad.position2()
                && p3 == quad.position3()
                && mat == quad.materialInfo()
                && normals == quad.bakedNormals()
                && colors == quad.bakedColors())
                ? quad
                : new BakedQuad(
                        p0, p1, p2, p3,
                        quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
                        quad.direction(), mat, normals, colors
                );

        if (QUAD_POOL.size() < MAX_QUAD_ENTRIES) {
            BakedQuad raced = QUAD_POOL.putIfAbsent(target, target);
            if (raced != null) {
                return raced;
            }
        }
        return target;
    }

    private static Vector3fc internVec(Vector3fc vec) {
        if (vec == null) {
            return null;
        }
        Vector3fc existing = VEC_POOL.get(vec);
        if (existing != null) {
            return existing;
        }
        Vector3fc copy = new Vector3f(vec.x(), vec.y(), vec.z());
        if (VEC_POOL.size() < MAX_VEC_ENTRIES) {
            Vector3fc raced = VEC_POOL.putIfAbsent(copy, copy);
            return raced != null ? raced : copy;
        }
        return copy;
    }

    private static BakedQuad.MaterialInfo internMaterial(BakedQuad.MaterialInfo info) {
        if (info == null) {
            return null;
        }
        BakedQuad.MaterialInfo existing = MATERIAL_POOL.get(info);
        if (existing != null) {
            return existing;
        }
        if (MATERIAL_POOL.size() < MAX_META_ENTRIES) {
            BakedQuad.MaterialInfo raced = MATERIAL_POOL.putIfAbsent(info, info);
            return raced != null ? raced : info;
        }
        return info;
    }

    private static BakedNormals internNormals(BakedNormals normals) {
        if (normals == null) {
            return null;
        }
        BakedNormals existing = NORMALS_POOL.get(normals);
        if (existing != null) {
            return existing;
        }
        if (NORMALS_POOL.size() < MAX_META_ENTRIES) {
            BakedNormals raced = NORMALS_POOL.putIfAbsent(normals, normals);
            return raced != null ? raced : normals;
        }
        return normals;
    }

    private static BakedColors internColors(BakedColors colors) {
        if (colors == null) {
            return null;
        }
        BakedColors existing = COLORS_POOL.get(colors);
        if (existing != null) {
            return existing;
        }
        if (COLORS_POOL.size() < MAX_META_ENTRIES) {
            BakedColors raced = COLORS_POOL.putIfAbsent(colors, colors);
            return raced != null ? raced : colors;
        }
        return colors;
    }

    public static void clear() {
        QUAD_POOL.clear();
        VEC_POOL.clear();
        MATERIAL_POOL.clear();
        NORMALS_POOL.clear();
        COLORS_POOL.clear();
    }
}
