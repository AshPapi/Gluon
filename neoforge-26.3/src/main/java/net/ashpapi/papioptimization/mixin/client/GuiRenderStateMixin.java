package net.ashpapi.papioptimization.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.ashpapi.papioptimization.access.PapiGuiNodeBounds;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.ScreenArea;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@OnlyIn(Dist.CLIENT)
@Mixin(value = GuiRenderState.class, priority = 1500)
public abstract class GuiRenderStateMixin {

    @WrapOperation(
        method = "navigateToAboveHighestElementWithIntersectingBounds",
        at = {
            @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState$Node;elementStates:Ljava/util/List;", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState$Node;itemStates:Ljava/util/List;", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState$Node;textStates:Ljava/util/List;", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState$Node;picturesInPictureStates:Ljava/util/List;", opcode = Opcodes.GETFIELD)
        }
    )
    private List<? extends ScreenArea> papi$pruneNonIntersectingGuiNode(
            @Coerce Object node,
            Operation<List<? extends ScreenArea>> original,
            @Local(argsOnly = true) ScreenRectangle bounds
    ) {
        if (node instanceof PapiGuiNodeBounds nodeBounds
                && !nodeBounds.papi$mayIntersect(bounds.left(), bounds.top(), bounds.right(), bounds.bottom())) {
            return null;
        }
        return original.call(node);
    }

    @WrapMethod(method = "hasIntersection")
    private boolean papi$fastIndexedHasIntersection(
            ScreenRectangle bounds,
            List<? extends ScreenArea> list,
            Operation<Boolean> original
    ) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        int left = bounds.left();
        int top = bounds.top();
        int right = bounds.right();
        int bottom = bounds.bottom();
        for (int i = list.size() - 1; i >= 0; i--) {
            ScreenRectangle other = list.get(i).bounds();
            if (other != null
                    && other.left() < right
                    && other.right() > left
                    && other.top() < bottom
                    && other.bottom() > top) {
                return true;
            }
        }
        return false;
    }
}
