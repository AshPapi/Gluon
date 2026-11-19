package net.ashpapi.papioptimization.mixin.entity.villager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SecondaryPoiSensor;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PapiOptimization Secondary POI Sensor Fast Skip:
 * In vanilla 26.3, SecondaryPoiSensor scans 405 blocks around every villager every 40 ticks.
 * However, only Farmers actually have secondary POIs (farmland, composters).
 * All 13 other professions (Librarians, Armorers, Clerics, etc.) have empty secondary POIs.
 * Skipping this scan for non-farmer professions saves 9,000+ useless getBlockState calls
 * per second in trading halls with 100% vanilla parity.
 */
@Mixin(value = SecondaryPoiSensor.class, priority = 1500)
public class SecondaryPoiSensorOptimizationMixin {

    @Inject(method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;)V",
            at = @At("HEAD"), cancellable = true)
    private void papi$fastSkipEmptySecondaryPoi(ServerLevel level, Villager villager, CallbackInfo ci) {
        VillagerProfession profession = villager.getVillagerData().profession().value();
        if (profession.secondaryPoi().isEmpty()) {
            villager.getBrain().eraseMemory(MemoryModuleType.SECONDARY_JOB_SITE);
            ci.cancel();
        }
    }
}
