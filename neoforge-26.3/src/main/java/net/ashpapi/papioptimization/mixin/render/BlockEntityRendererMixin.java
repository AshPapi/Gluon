package net.ashpapi.papioptimization.mixin.render;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockEntityRenderer.class, priority = 1500)
public interface BlockEntityRendererMixin<T extends BlockEntity> {

    @Shadow int getViewDistance();

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void papi$fastShouldRender(T blockEntity, Vec3 cameraPosition, CallbackInfoReturnable<Boolean> cir) {
        BlockPos pos = blockEntity.getBlockPos();
        double dx = (pos.getX() + 0.5) - cameraPosition.x;
        double dy = (pos.getY() + 0.5) - cameraPosition.y;
        double dz = (pos.getZ() + 0.5) - cameraPosition.z;
        double maxDist = this.getViewDistance();
        cir.setReturnValue(dx * dx + dy * dy + dz * dz < maxDist * maxDist);
    }
}
