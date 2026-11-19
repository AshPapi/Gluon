package net.ashpapi.papioptimization.mixin.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestItemSensor;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Mixin(value = NearestItemSensor.class, priority = 1500)
public abstract class NearestItemSensorMixin {

    @Inject(method = "doTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Mob;)V", at = @At("HEAD"), cancellable = true)
    private void papi$fastDoTick(ServerLevel level, Mob body, CallbackInfo ci) {
        Brain<?> brain = body.getBrain();
        AABB box = body.getBoundingBox().inflate(32.0, 16.0, 32.0);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box, item -> true);
        if (items.isEmpty()) {
            brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
            ci.cancel();
            return;
        }
        items.sort(Comparator.comparingDouble(body::distanceToSqr));
        ItemEntity found = null;
        for (int i = 0, size = items.size(); i < size; i++) {
            ItemEntity item = items.get(i);
            if (item.closerThan(body, 32.0) && body.wantsToPickUp(level, item.getItem()) && body.hasLineOfSight(item)) {
                found = item;
                break;
            }
        }
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, Optional.ofNullable(found));
        ci.cancel();
    }
}
