package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.ref.WeakReference;
import net.ashpapi.papioptimization.worldgen.JigsawStateCache;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = JigsawReplacementProcessor.class, priority = 1500)
public abstract class JigsawReplacementProcessorMixin {

    @Unique
    private static final ThreadLocal<JigsawStateCache> PAPI$STATE_CACHE = ThreadLocal.withInitial(JigsawStateCache::new);

    @WrapMethod(method = "processBlock")
    private StructureTemplate.@Nullable StructureBlockInfo papi$fastJigsawReplacement(
            LevelReader level,
            BlockPos targetPosition,
            BlockPos referencePos,
            BlockPos templateRelativePos,
            StructureTemplate.StructureBlockInfo processedBlockInfo,
            StructurePlaceSettings settings,
            Operation<StructureTemplate.@Nullable StructureBlockInfo> original
    ) {
        BlockState state = processedBlockInfo.state();
        if (!state.is(Blocks.JIGSAW)) {
            return processedBlockInfo;
        }

        if (processedBlockInfo.nbt() == null) {
            return original.call(level, targetPosition, referencePos, templateRelativePos, processedBlockInfo, settings);
        }

        String stateString = processedBlockInfo.nbt().getStringOr("final_state", "minecraft:air");
        if ("minecraft:air".equals(stateString)) {
            return new StructureTemplate.StructureBlockInfo(processedBlockInfo.pos(), Blocks.AIR.defaultBlockState(), null);
        }

        HolderLookup<Block> blockLookup = level.holderLookup(Registries.BLOCK);
        JigsawStateCache cache = PAPI$STATE_CACHE.get();
        if (cache.lookupRef.get() != blockLookup) {
            cache.map.clear();
            cache.lookupRef = new WeakReference<>(blockLookup);
        }

        BlockState cached = cache.map.get(stateString);
        if (cached != null) {
            return cached.is(Blocks.STRUCTURE_VOID)
                    ? null
                    : new StructureTemplate.StructureBlockInfo(processedBlockInfo.pos(), cached, null);
        }

        try {
            BlockState parsed = BlockStateParser.parseForBlock(blockLookup, stateString, true).blockState();
            if (cache.map.size() >= 512) {
                cache.map.clear();
            }
            cache.map.put(stateString, parsed);
            return parsed.is(Blocks.STRUCTURE_VOID)
                    ? null
                    : new StructureTemplate.StructureBlockInfo(processedBlockInfo.pos(), parsed, null);
        } catch (Exception e) {
            return original.call(level, targetPosition, referencePos, templateRelativePos, processedBlockInfo, settings);
        }
    }
}
