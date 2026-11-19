package net.ashpapi.papioptimization.mixin.render;

import net.ashpapi.papioptimization.client.PapiEntityCullingBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityRenderer.class, priority = 1500)
public abstract class EntityRendererShadowFastMixin<T extends Entity, S extends EntityRenderState> implements PapiEntityCullingBox {

    @Shadow
    protected abstract float getShadowRadius(S state);

    @Shadow
    protected abstract float getShadowStrength(S state);

    @Shadow
    protected abstract AABB getBoundingBoxForCulling(T entity, float partialTick);

    @Override
    @SuppressWarnings("unchecked")
    public AABB papi$getCullingBox(Entity entity, float partialTick) {
        return this.getBoundingBoxForCulling((T) entity, partialTick);
    }

    @Inject(method = "extractShadow", at = @At("HEAD"), cancellable = true)
    private void papi$fastExtractShadow(S state, Minecraft mc, Level level, CallbackInfo ci) {
        state.shadowPieces.clear();
        if (mc.options.entityShadows().get() && !state.isInvisible) {
            float radius = Math.min(this.getShadowRadius(state), 32.0F);
            state.shadowRadius = radius;
            if (radius > 0.0F) {
                double distSq = state.distanceToCameraSq;
                float strength = (float) ((1.0 - distSq / 256.0) * (double) this.getShadowStrength(state));
                if (strength > 0.0F) {
                    int minX = Mth.floor(state.x - (double) radius);
                    int maxX = Mth.floor(state.x + (double) radius);
                    int minZ = Mth.floor(state.z - (double) radius);
                    int maxZ = Mth.floor(state.z + (double) radius);
                    float yDepth = Math.min(strength / 0.5F - 1.0F, radius);
                    int minY = Mth.floor(state.y - (double) yDepth);
                    int maxY = Mth.floor(state.y);

                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();
                    DimensionType dimType = level.dimensionType();

                    int lastChunkX = Integer.MIN_VALUE;
                    int lastChunkZ = Integer.MIN_VALUE;
                    ChunkAccess chunk = null;

                    for (int z = minZ; z <= maxZ; z++) {
                        int chunkZ = z >> 4;
                        for (int x = minX; x <= maxX; x++) {
                            int chunkX = x >> 4;
                            if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                                lastChunkX = chunkX;
                                lastChunkZ = chunkZ;
                                chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                            }
                            if (chunk == null) {
                                continue;
                            }
                            pos.set(x, 0, z);
                            belowPos.set(x, 0, z);
                            for (int y = minY; y <= maxY; y++) {
                                float pieceStrength = strength - (float) (state.y - (double) y) * 0.5F;
                                if (pieceStrength <= 0.0F) {
                                    continue;
                                }
                                belowPos.setY(y - 1);
                                BlockState belowState = chunk.getBlockState(belowPos);
                                if (belowState.getRenderShape() != RenderShape.INVISIBLE
                                    && belowState.isCollisionShapeFullBlock(chunk, belowPos)) {
                                    pos.setY(y);
                                    int brightness = level.getMaxLocalRawBrightness(pos);
                                    if (brightness > 3) {
                                        VoxelShape shape = belowState.getShape(chunk, belowPos);
                                        if (!shape.isEmpty()) {
                                            float alpha = Mth.clamp(
                                                pieceStrength * 0.5F * Lightmap.getBrightness(dimType, brightness),
                                                0.0F,
                                                1.0F
                                            );
                                            float relX = (float) ((double) x - state.x);
                                            float relY = (float) ((double) y - state.y);
                                            float relZ = (float) ((double) z - state.z);
                                            state.shadowPieces.add(new EntityRenderState.ShadowPiece(relX, relY, relZ, shape, alpha));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            state.shadowRadius = 0.0F;
        }
        ci.cancel();
    }
}
