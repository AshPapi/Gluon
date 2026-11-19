package net.ashpapi.papioptimization.mixin.threading;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Util.class, priority = 1500)
public abstract class UtilWorkerPriorityMixin {

    @WrapMethod(method = "maxAllowedExecutorThreads")
    private static int papi$maxAllowedExecutorThreads(Operation<Integer> original) {
        if (System.getProperty("max.bg.threads") != null) {
            return original.call();
        }
        int cpus = Runtime.getRuntime().availableProcessors();
        return cpus > 4 ? Math.clamp(cpus - 2, 2, 10) : Math.max(1, cpus - 1);
    }

    @WrapOperation(
        method = "makeExecutor",
        at = @At(
            value = "NEW",
            target = "(ILjava/util/concurrent/ForkJoinPool$ForkJoinWorkerThreadFactory;Ljava/lang/Thread$UncaughtExceptionHandler;Z)Ljava/util/concurrent/ForkJoinPool;"
        )
    )
    private static ForkJoinPool papi$lowerWorkerPriority(
            int parallelism,
            ForkJoinPool.ForkJoinWorkerThreadFactory factory,
            Thread.UncaughtExceptionHandler handler,
            boolean asyncMode,
            Operation<ForkJoinPool> original
    ) {
        ForkJoinPool.ForkJoinWorkerThreadFactory wrappedFactory = pool -> {
            ForkJoinWorkerThread thread = factory.newThread(pool);
            if (thread != null) {
                thread.setPriority(Thread.NORM_PRIORITY - 1);
            }
            return thread;
        };
        return original.call(parallelism, wrappedFactory, handler, asyncMode);
    }
}
