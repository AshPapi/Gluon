package net.ashpapi.papioptimization.mixin.lighting;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optimizes the light task queue execution in ThreadedLevelLightEngine.
 * Vanilla uses iterator.remove() in a loop to remove up to 1000 items from ObjectArrayList.
 * In FastUtil, each single remove() shifts all remaining elements via System.arraycopy,
 * causing O(N*M) copying overhead (millions of reference copies when queue backlog accumulates).
 *
 * Replacing this with a single batch removeElements(0, totalSize) reduces this to exactly 1 System.arraycopy,
 * completely eliminating light queue stutters during fast flight / chunk generation.
 */
@Mixin(value = ThreadedLevelLightEngine.class, priority = 1500)
public abstract class ThreadedLevelLightEngineMixin extends LevelLightEngine {

    @Shadow
    @Final
    private ObjectList<Pair<?, Runnable>> lightTasks;

    protected ThreadedLevelLightEngineMixin() {
        super(null, false, false);
    }

    @Inject(method = "runUpdate", at = @At("HEAD"), cancellable = true)
    private void papi$batchRunUpdate(CallbackInfo ci) {
        int totalSize = Math.min(this.lightTasks.size(), 1000);
        if (totalSize == 0) {
            super.runLightUpdates();
            ci.cancel();
            return;
        }

        ObjectArrayList<Pair<?, Runnable>> batch = new ObjectArrayList<>(totalSize);
        for (int i = 0; i < totalSize; i++) {
            batch.add(this.lightTasks.get(i));
        }
        // 4. Batch remove: 1 System.arraycopy instead of 1000 individual array copies!
        if (this.lightTasks instanceof ObjectArrayList<Pair<?, Runnable>> arrayList) {
            arrayList.removeElements(0, totalSize);
        } else {
            this.lightTasks.subList(0, totalSize).clear();
        }

        // 1. Run PRE_UPDATE tasks (ordinal == 0) without allocating an ObjectListIterator
        for (int i = 0; i < totalSize; i++) {
            Pair<?, Runnable> task = batch.get(i);
            if (((Enum<?>) task.getFirst()).ordinal() == 0) { // PRE_UPDATE
                task.getSecond().run();
            }
        }

        // 2. Perform light propagation
        super.runLightUpdates();

        // 3. Run POST_UPDATE tasks (ordinal == 1)
        for (int i = 0; i < totalSize; i++) {
            Pair<?, Runnable> task = batch.get(i);
            if (((Enum<?>) task.getFirst()).ordinal() == 1) { // POST_UPDATE
                task.getSecond().run();
            }
        }

        ci.cancel();
    }
}
