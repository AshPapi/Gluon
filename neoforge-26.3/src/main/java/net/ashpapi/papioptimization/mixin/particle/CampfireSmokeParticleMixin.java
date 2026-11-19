package net.ashpapi.papioptimization.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ashpapi.papioptimization.core.BlockPosPool;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CampfireSmokeParticle.class, priority = 1500)
public abstract class CampfireSmokeParticleMixin extends SingleQuadParticle {

    protected CampfireSmokeParticleMixin(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
    }

    @WrapOperation(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/CampfireSmokeParticle;move(DDD)V")
    )
    private void papi$fastAirPathMove(
            CampfireSmokeParticle particle,
            double xa,
            double ya,
            double za,
            Operation<Void> original
    ) {
        AABB box = this.getBoundingBox();
        AABB moved = box.move(xa, ya, za);
        int minX = Mth.floor(Math.min(box.minX, moved.minX));
        int maxX = Mth.floor(Math.max(box.maxX, moved.maxX));
        int minY = Mth.floor(Math.min(box.minY, moved.minY));
        int maxY = Mth.floor(Math.max(box.maxY, moved.maxY));
        int minZ = Mth.floor(Math.min(box.minZ, moved.minZ));
        int maxZ = Mth.floor(Math.max(box.maxZ, moved.maxZ));

        if ((maxX - minX) <= 1 && (maxY - minY) <= 1 && (maxZ - minZ) <= 1) {
            int mark = BlockPosPool.mark();
            try {
                BlockPos.MutableBlockPos mut = BlockPosPool.get();
                boolean allAir = true;
                outer:
                for (int bx = minX; bx <= maxX; bx++) {
                    for (int by = minY; by <= maxY; by++) {
                        for (int bz = minZ; bz <= maxZ; bz++) {
                            if (!this.level.getBlockState(mut.set(bx, by, bz)).isAir()) {
                                allAir = false;
                                break outer;
                            }
                        }
                    }
                }
                if (allAir) {
                    this.setBoundingBox(moved);
                    this.setLocationFromBoundingbox();
                    this.onGround = false;
                    return;
                }
            } finally {
                BlockPosPool.reset(mark);
            }
        }

        original.call(particle, xa, ya, za);
    }
}
