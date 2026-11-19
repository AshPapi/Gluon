package net.ashpapi.papioptimization.mixin.poi;

import net.ashpapi.papioptimization.access.PapiPoiSectionFast;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

@Mixin(value = PoiManager.class, priority = 1500)
public abstract class PoiManagerMixin {

    @Inject(method = "findClosest(Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private void papi$fastFindClosest(
            Predicate<Holder<PoiType>> typePredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy,
            CallbackInfoReturnable<Optional<BlockPos>> cir
    ) {
        cir.setReturnValue(papi$searchClosestPoi(typePredicate, pos -> true, origin, radius, occupancy));
    }

    @Inject(method = "findClosest(Ljava/util/function/Predicate;Ljava/util/function/Predicate;Lnet/minecraft/core/BlockPos;ILnet/minecraft/world/entity/ai/village/poi/PoiManager$Occupancy;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private void papi$fastFindClosestFiltered(
            Predicate<Holder<PoiType>> typePredicate,
            Predicate<BlockPos> posPredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy,
            CallbackInfoReturnable<Optional<BlockPos>> cir
    ) {
        cir.setReturnValue(papi$searchClosestPoi(typePredicate, posPredicate, origin, radius, occupancy));
    }

    @Unique
    @SuppressWarnings("unchecked")
    private Optional<BlockPos> papi$searchClosestPoi(
            Predicate<Holder<PoiType>> typePredicate,
            Predicate<BlockPos> posPredicate,
            BlockPos origin,
            int radius,
            PoiManager.Occupancy occupancy
    ) {
        if (radius < 0) {
            return Optional.empty();
        }
        SectionStorageAccessor<PoiSection> storage = (SectionStorageAccessor<PoiSection>) (Object) this;
        int ox = origin.getX();
        int oz = origin.getZ();
        int originChunkX = SectionPos.blockToSectionCoord(ox);
        int originChunkZ = SectionPos.blockToSectionCoord(oz);
        int chunkRadius = Math.floorDiv(radius, 16) + 1;
        double maxRadiusSq = (double) radius * (double) radius;

        double closestDistSq = Double.MAX_VALUE;
        BlockPos closestPos = null;

        LevelHeightAccessor heightAccessor = storage.papi$getLevelHeightAccessor();
        int minSecY = heightAccessor.getMinSectionY();
        int maxSecY = heightAccessor.getMaxSectionY();

        Predicate<? super PoiRecord> occupancyTest = occupancy.getTest();

        for (int r = 0; r <= chunkRadius; r++) {
            if (r > 1) {
                double minRingDist = (double) ((r - 1) * 16);
                if (minRingDist * minRingDist >= closestDistSq) {
                    break;
                }
            }

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }

                    int cx = originChunkX + dx;
                    int cz = originChunkZ + dz;

                    int chunkMinX = cx << 4;
                    int chunkMaxX = chunkMinX + 15;
                    int chunkMinZ = cz << 4;
                    int chunkMaxZ = chunkMinZ + 15;

                    int clX = ox < chunkMinX ? chunkMinX : (Math.min(ox, chunkMaxX));
                    int clZ = oz < chunkMinZ ? chunkMinZ : (Math.min(oz, chunkMaxZ));

                    double dX = ox - clX;
                    double dZ = oz - clZ;
                    double minChunkDistSq = dX * dX + dZ * dZ;

                    if (minChunkDistSq >= closestDistSq || minChunkDistSq > maxRadiusSq) {
                        continue;
                    }

                    for (int secY = minSecY; secY <= maxSecY; secY++) {
                        long sectionKey = SectionPos.asLong(cx, secY, cz);
                        Optional<PoiSection> opt = storage.papi$getOrLoad(sectionKey);
                        if (opt == null || opt.isEmpty()) continue;

                        PoiSection section = opt.get();
                        if (section instanceof PapiPoiSectionFast fast && !fast.papi$mayMatchOccupancy(occupancy)) {
                            continue;
                        }
                        Map<Holder<PoiType>, Set<PoiRecord>> byType = ((PoiSectionAccessor) section).papi$getByType();
                        if (byType == null || byType.isEmpty()) continue;

                        for (Map.Entry<Holder<PoiType>, Set<PoiRecord>> entry : byType.entrySet()) {
                            if (typePredicate.test(entry.getKey())) {
                                for (PoiRecord record : entry.getValue()) {
                                    if (occupancyTest.test(record)) {
                                        BlockPos p = record.getPos();
                                        double distSq = p.distSqr(origin);
                                        if (distSq <= maxRadiusSq && distSq < closestDistSq && posPredicate.test(p)) {
                                            closestDistSq = distSq;
                                            closestPos = p;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return closestPos != null ? Optional.of(closestPos) : Optional.empty();
    }
}
