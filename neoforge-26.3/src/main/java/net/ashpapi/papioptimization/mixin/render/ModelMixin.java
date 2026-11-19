package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

@Mixin(value = Model.class, priority = 1500)
public abstract class ModelMixin {

    @Shadow
    @Final
    private List<ModelPart> allParts;

    @WrapMethod(method = "resetPose")
    private void papi$fastResetPose(Operation<Void> original) {
        List<ModelPart> parts = this.allParts;
        for (int i = 0, n = parts.size(); i < n; i++) {
            parts.get(i).resetPose();
        }
    }
}
