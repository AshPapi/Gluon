package net.ashpapi.papioptimization.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ashpapi.papioptimization.access.PapiRenderBoxCache;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ChestRenderer.class, priority = 1500)
public abstract class StaticBlockEntityRenderMixin {

    @WrapMethod(method = "getRenderBoundingBox(Lnet/minecraft/world/level/block/entity/BlockEntity;)Lnet/minecraft/world/phys/AABB;")
    private AABB papi$fastChestRenderBox(BlockEntity blockEntity, Operation<AABB> original) {
        return ((PapiRenderBoxCache) blockEntity).papi$getOrComputeChestBox(blockEntity.getBlockPos());
    }
}
