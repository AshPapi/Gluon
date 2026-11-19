package net.ashpapi.papioptimization.mixin.chunk;

import net.ashpapi.papioptimization.access.PapiLevelBlockEpoch;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LevelChunk.class, priority = 1500)
public abstract class LevelChunkFastBlockEntityMixin extends ChunkAccess {

    public LevelChunkFastBlockEntityMixin(
        ChunkPos chunkPos,
        UpgradeData upgradeData,
        LevelHeightAccessor levelHeightAccessor,
        PalettedContainerFactory palettedContainerFactory,
        long inhabitedTime,
        LevelChunkSection[] sections,
        BlendingData blendingData
    ) {
        super(chunkPos, upgradeData, levelHeightAccessor, palettedContainerFactory, inhabitedTime, sections, blendingData);
    }

    @Shadow
    private BlockEntity promotePendingBlockEntity(BlockPos pos, CompoundTag tag) {
        return null;
    }

    @Shadow
    private BlockEntity createBlockEntity(BlockPos pos) {
        return null;
    }

    @Shadow
    @Final
    Level level;

    @Shadow
    public abstract void addAndRegisterBlockEntity(BlockEntity blockEntity);

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void papi$notifyBlockEpochOnStateChange(
            BlockPos pos,
            BlockState state,
            int flags,
            CallbackInfoReturnable<BlockState> cir
    ) {
        if (cir.getReturnValue() != null && this.level != null) {
            ((PapiLevelBlockEpoch) this.level).papi$bumpBlockEpoch(pos.asLong());
        }
    }

    @Inject(
        method = "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void papi$fastGetBlockEntity(
        BlockPos pos,
        LevelChunk.EntityCreationType creationType,
        CallbackInfoReturnable<BlockEntity> cir
    ) {
        BlockEntity blockEntity = null;
        if (!this.blockEntities.isEmpty()) {
            blockEntity = this.blockEntities.get(pos);
            if (blockEntity != null && blockEntity.isRemoved()) {
                this.blockEntities.remove(pos);
                blockEntity = null;
            }
        }
        if (blockEntity == null && !this.pendingBlockEntities.isEmpty()) {
            CompoundTag tag = this.pendingBlockEntities.remove(pos);
            if (tag != null) {
                BlockEntity promoted = this.promotePendingBlockEntity(pos, tag);
                if (promoted != null) {
                    cir.setReturnValue(promoted);
                    return;
                }
            }
        }
        if (blockEntity == null && creationType == LevelChunk.EntityCreationType.IMMEDIATE) {
            blockEntity = this.createBlockEntity(pos);
            if (blockEntity != null) {
                this.addAndRegisterBlockEntity(blockEntity);
            }
        }
        cir.setReturnValue(blockEntity);
    }
}
