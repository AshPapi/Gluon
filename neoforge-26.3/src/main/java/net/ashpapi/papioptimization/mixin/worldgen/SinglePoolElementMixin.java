package net.ashpapi.papioptimization.mixin.worldgen;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import net.ashpapi.papioptimization.worldgen.PapiSinglePoolCache;
import net.ashpapi.papioptimization.worldgen.PapiStructureEpoch;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SinglePoolElement.class, priority = 1500)
public abstract class SinglePoolElementMixin {

    @Shadow
    private StructureTemplate getTemplate(StructureTemplateManager structureTemplateManager) {
        throw new AssertionError();
    }

    @Unique
    private volatile PapiSinglePoolCache papi$cache;

    @Inject(method = "getTemplate", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetTemplate(StructureTemplateManager structureTemplateManager, CallbackInfoReturnable<StructureTemplate> cir) {
        int epoch = PapiStructureEpoch.current();
        PapiSinglePoolCache cache = this.papi$cache;
        if (cache != null && cache.epoch() == epoch && cache.managerRef().get() == structureTemplateManager) {
            SoftReference<StructureTemplate> ref = cache.templateRef();
            if (ref != null) {
                StructureTemplate cached = ref.get();
                if (cached != null) {
                    cir.setReturnValue(cached);
                }
            }
        }
    }

    @Inject(method = "getTemplate", at = @At("RETURN"))
    private void papi$cacheResolvedTemplate(StructureTemplateManager structureTemplateManager, CallbackInfoReturnable<StructureTemplate> cir) {
        StructureTemplate val = cir.getReturnValue();
        if (val != null) {
            int epoch = PapiStructureEpoch.current();
            synchronized (this) {
                PapiSinglePoolCache cur = this.papi$cache;
                if (cur != null && cur.epoch() == epoch && cur.managerRef().get() == structureTemplateManager) {
                    SoftReference<StructureTemplate> tRef = cur.templateRef();
                    if (tRef == null || tRef.get() != val) {
                        this.papi$cache = new PapiSinglePoolCache(cur.managerRef(), new SoftReference<>(val), cur.boxes(), epoch);
                    }
                } else {
                    this.papi$cache = new PapiSinglePoolCache(
                            new WeakReference<>(structureTemplateManager),
                            new SoftReference<>(val),
                            new BoundingBox[4],
                            epoch
                    );
                }
            }
        }
    }

    @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetBoundingBox(StructureTemplateManager structureTemplateManager, BlockPos position, Rotation rotation, CallbackInfoReturnable<BoundingBox> cir) {
        int rotIdx = rotation.ordinal();
        int epoch = PapiStructureEpoch.current();
        PapiSinglePoolCache cache = this.papi$cache;
        BoundingBox baseBox = (cache != null && cache.epoch() == epoch && cache.managerRef().get() == structureTemplateManager)
                ? cache.boxes()[rotIdx]
                : null;
        if (baseBox == null) {
            StructureTemplate template = this.getTemplate(structureTemplateManager);
            BoundingBox computed = template.getBoundingBox(new StructurePlaceSettings().setRotation(rotation), BlockPos.ZERO);
            if (PapiStructureEpoch.current() == epoch) {
                synchronized (this) {
                    PapiSinglePoolCache cur = this.papi$cache;
                    if (cur != null && cur.epoch() == epoch && cur.managerRef().get() == structureTemplateManager) {
                        baseBox = cur.boxes()[rotIdx];
                        if (baseBox == null) {
                            BoundingBox[] nextBoxes = cur.boxes().clone();
                            nextBoxes[rotIdx] = computed;
                            SoftReference<StructureTemplate> tRef = (cur.templateRef() != null && cur.templateRef().get() != null)
                                    ? cur.templateRef()
                                    : new SoftReference<>(template);
                            this.papi$cache = new PapiSinglePoolCache(cur.managerRef(), tRef, nextBoxes, epoch);
                            baseBox = computed;
                        }
                    } else {
                        BoundingBox[] nextBoxes = new BoundingBox[4];
                        nextBoxes[rotIdx] = computed;
                        this.papi$cache = new PapiSinglePoolCache(
                                new WeakReference<>(structureTemplateManager),
                                new SoftReference<>(template),
                                nextBoxes,
                                epoch
                        );
                        baseBox = computed;
                    }
                }
            } else {
                baseBox = computed;
            }
        }
        cir.setReturnValue(baseBox.moved(position.getX(), position.getY(), position.getZ()));
    }

    @Inject(method = "getDataMarkers", at = @At("HEAD"), cancellable = true)
    private void papi$fastGetDataMarkers(StructureTemplateManager structureTemplateManager, BlockPos position, Rotation rotation, boolean absolute, CallbackInfoReturnable<List<StructureTemplate.StructureBlockInfo>> cir) {
        StructureTemplate template = this.getTemplate(structureTemplateManager);
        ObjectArrayList<StructureTemplate.StructureBlockInfo> structureBlocks = template.filterBlocks(position, new StructurePlaceSettings().setRotation(rotation), Blocks.STRUCTURE_BLOCK, absolute);
        ArrayList<StructureTemplate.StructureBlockInfo> dataMarkers = Lists.newArrayList();

        int size = structureBlocks.size();
        for (int i = 0; i < size; i++) {
            StructureTemplate.StructureBlockInfo info = structureBlocks.get(i);
            CompoundTag nbt = info.nbt();
            if (nbt != null && "DATA".equals(nbt.getStringOr("mode", ""))) {
                dataMarkers.add(info);
            }
        }
        cir.setReturnValue(dataMarkers);
    }
}
