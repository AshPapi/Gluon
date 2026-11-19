package net.ashpapi.papioptimization.mixin.poi;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Set;

@Mixin(value = PoiSection.class, priority = 1500)
public interface PoiSectionAccessor {
    @Accessor("byType")
    Map<Holder<PoiType>, Set<PoiRecord>> papi$getByType();
}
