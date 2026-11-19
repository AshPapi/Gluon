package net.ashpapi.papioptimization.mixin.client;

import com.google.common.collect.Multimap;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.client.PapiQuadCanonicalizer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = QuadCollection.Builder.class, priority = 1500)
public abstract class QuadCollectionBuilderDeduplicationMixin {

    @Shadow @Final private Multimap<Direction, BakedQuad> culledFaces;

    @Unique
    private final BakedQuad[] papi$recentUnculled = new BakedQuad[4];

    @Unique
    private int papi$recentUnculledIdx = 0;

    @WrapMethod(method = "addCulledFace")
    private QuadCollection.Builder papi$dedupCulledFace(Direction direction, BakedQuad quad, Operation<QuadCollection.Builder> original) {
        BakedQuad canonical = PapiQuadCanonicalizer.canonicalize(quad);
        java.util.Collection<BakedQuad> existing = this.culledFaces.get(direction);
        if (!existing.isEmpty() && existing.size() <= 16 && existing.contains(canonical)) {
            return (QuadCollection.Builder) (Object) this;
        }
        return original.call(direction, canonical);
    }

    @WrapMethod(method = "addUnculledFace")
    private QuadCollection.Builder papi$dedupUnculledFace(BakedQuad quad, Operation<QuadCollection.Builder> original) {
        BakedQuad canonical = PapiQuadCanonicalizer.canonicalize(quad);
        for (int i = 0; i < 4; i++) {
            BakedQuad prev = this.papi$recentUnculled[i];
            if (prev != null && (prev == canonical || prev.equals(canonical))) {
                return (QuadCollection.Builder) (Object) this;
            }
        }
        int idx = this.papi$recentUnculledIdx;
        this.papi$recentUnculled[idx] = canonical;
        this.papi$recentUnculledIdx = (idx + 1) & 3;
        return original.call(canonical);
    }
}
