package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@OnlyIn(Dist.CLIENT)
@Mixin(targets = "net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer$Group", priority = 1500)
public abstract class RenderTypeFeatureRendererGroupMixin {

    @Shadow
    @Final
    private boolean canReorder;

    @Unique
    private Object2IntOpenHashMap<PreparedRenderType> papi$renderTypeIndices;

    @WrapOperation(
        method = "getOrAddDraw",
        at = @At(value = "INVOKE", target = "Ljava/util/List;indexOf(Ljava/lang/Object;)I")
    )
    private int papi$fastLookupPreparedRenderType(List<?> list, Object prepared, Operation<Integer> original) {
        Object2IntOpenHashMap<PreparedRenderType> map = this.papi$renderTypeIndices;
        if (map == null) {
            return -1;
        }
        return map.getInt(prepared);
    }

    @WrapOperation(
        method = "getOrAddDraw",
        at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 1)
    )
    private boolean papi$recordPreparedRenderTypeIndex(List<Object> list, Object prepared, Operation<Boolean> original) {
        boolean added = original.call(list, prepared);
        if (added && this.canReorder && prepared instanceof PreparedRenderType prt) {
            Object2IntOpenHashMap<PreparedRenderType> map = this.papi$renderTypeIndices;
            if (map == null) {
                map = new Object2IntOpenHashMap<>(16);
                map.defaultReturnValue(-1);
                this.papi$renderTypeIndices = map;
            }
            map.putIfAbsent(prt, list.size() - 1);
        }
        return added;
    }
}
