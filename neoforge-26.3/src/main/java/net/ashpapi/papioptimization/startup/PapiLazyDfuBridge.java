package net.ashpapi.papioptimization.startup;

import com.mojang.datafixers.DSL;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class PapiLazyDfuBridge {

    private static final Set<String> OPTIMIZED_TYPES = ConcurrentHashMap.newKeySet();
    private static volatile Object vanillaFixer = null;
    private static volatile Consumer<DSL.TypeReference> optimizer = null;

    private PapiLazyDfuBridge() {}

    public static void registerOptimizer(Object fixerInstance, Consumer<DSL.TypeReference> callback) {
        vanillaFixer = fixerInstance;
        optimizer = callback;
    }

    public static void ensureOptimized(Object fixerInstance, DSL.TypeReference type) {
        if (fixerInstance == null || fixerInstance != vanillaFixer) {
            return;
        }
        Consumer<DSL.TypeReference> cb = optimizer;
        if (cb != null && type != null && OPTIMIZED_TYPES.add(type.typeName())) {
            cb.accept(type);
        }
    }
}
