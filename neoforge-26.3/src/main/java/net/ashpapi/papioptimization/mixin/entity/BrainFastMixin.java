package net.ashpapi.papioptimization.mixin.entity;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemorySlot;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(value = Brain.class, priority = 1500)
public abstract class BrainFastMixin<E extends LivingEntity> {

    @Shadow @Final @Mutable private Map<MemoryModuleType<?>, MemorySlot<?>> memories;
    @Shadow @Final private Map<Integer, Map<Activity, Set<BehaviorControl<? super E>>>> availableBehaviorsByPriority;

    @Inject(
        method = {
            "<init>(Ljava/util/Collection;Ljava/util/Collection;Ljava/util/List;Lnet/minecraft/world/entity/ai/memory/MemoryMap;Lnet/minecraft/util/RandomSource;)V",
            "<init>()V"
        },
        at = @At("RETURN")
    )
    private void papi$replaceMemoriesWithReferenceMap(CallbackInfo ci) {
        if (!(this.memories instanceof Reference2ObjectOpenHashMap)) {
            this.memories = new Reference2ObjectOpenHashMap<>(this.memories);
        }
    }

    @Inject(method = "forgetOutdatedMemories", at = @At("HEAD"), cancellable = true)
    private void papi$fastForgetOutdatedMemories(CallbackInfo ci) {
        for (MemorySlot<?> slot : this.memories.values()) {
            slot.tick();
        }
        ci.cancel();
    }

    @Inject(method = "tickEachRunningBehavior", at = @At("HEAD"), cancellable = true)
    private void papi$fastTickEachRunningBehavior(ServerLevel level, E body, CallbackInfo ci) {
        long timestamp = level.getGameTime();
        List<BehaviorControl<? super E>> running = new ArrayList<>(16);

        for (Map<Activity, Set<BehaviorControl<? super E>>> byActivity : this.availableBehaviorsByPriority.values()) {
            for (Set<BehaviorControl<? super E>> behaviors : byActivity.values()) {
                for (BehaviorControl<? super E> behavior : behaviors) {
                    if (behavior.getStatus() == Behavior.Status.RUNNING) {
                        running.add(behavior);
                    }
                }
            }
        }

        for (int i = 0, size = running.size(); i < size; i++) {
            running.get(i).tickOrStop(level, body, timestamp);
        }
        ci.cancel();
    }

    @Inject(method = "stopAll", at = @At("HEAD"), cancellable = true)
    private void papi$fastStopAll(ServerLevel level, E body, CallbackInfo ci) {
        long timestamp = body.level().getGameTime();
        List<BehaviorControl<? super E>> running = new ArrayList<>(16);

        for (Map<Activity, Set<BehaviorControl<? super E>>> byActivity : this.availableBehaviorsByPriority.values()) {
            for (Set<BehaviorControl<? super E>> behaviors : byActivity.values()) {
                for (BehaviorControl<? super E> behavior : behaviors) {
                    if (behavior.getStatus() == Behavior.Status.RUNNING) {
                        running.add(behavior);
                    }
                }
            }
        }

        for (int i = 0, size = running.size(); i < size; i++) {
            running.get(i).doStop(level, body, timestamp);
        }
        ci.cancel();
    }
}
