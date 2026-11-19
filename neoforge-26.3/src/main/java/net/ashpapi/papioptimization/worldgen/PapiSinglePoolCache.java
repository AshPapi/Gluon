package net.ashpapi.papioptimization.worldgen;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public record PapiSinglePoolCache(
        WeakReference<StructureTemplateManager> managerRef,
        SoftReference<StructureTemplate> templateRef,
        BoundingBox[] boxes,
        int epoch
) {
}
