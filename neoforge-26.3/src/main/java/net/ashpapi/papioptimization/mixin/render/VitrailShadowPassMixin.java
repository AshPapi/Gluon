package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Coerce;

@OnlyIn(Dist.CLIENT)
@Pseudo
@Mixin(targets = "dev.vitrail.render.ShadowGeometry", priority = 1500, remap = false)
public abstract class VitrailShadowPassMixin {

    @WrapMethod(
        method = "gather(Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;Ldev/vitrail/pack/source/ShadowCasters;)V",
        remap = false,
        require = 0
    )
    private static void papi$trackVitrailShadowGather(
            Matrix4f shadowMatrix,
            Vec3 cameraPos,
            @Coerce Object casters,
            Operation<Void> original
    ) {
        PapiOcclusionEngine.enterShadowPass();
        try {
            original.call(shadowMatrix, cameraPos, casters);
        } finally {
            PapiOcclusionEngine.exitShadowPass();
        }
    }

    @WrapMethod(
        method = "gatherBlockEntities(Ldev/vitrail/render/ShadowGeometry$BlockEntityWalk;)V",
        remap = false,
        require = 0
    )
    private static void papi$trackVitrailShadowGatherBlockEntities(
            @Coerce Object walk,
            Operation<Void> original
    ) {
        PapiOcclusionEngine.enterShadowPass();
        try {
            original.call(walk);
        } finally {
            PapiOcclusionEngine.exitShadowPass();
        }
    }

    @WrapMethod(method = "draw(Lnet/minecraft/world/phys/Vec3;)V", remap = false, require = 0)
    private static void papi$trackVitrailShadowDraw(Vec3 cameraPos, Operation<Void> original) {
        PapiOcclusionEngine.enterShadowPass();
        try {
            original.call(cameraPos);
        } finally {
            PapiOcclusionEngine.exitShadowPass();
        }
    }
}
