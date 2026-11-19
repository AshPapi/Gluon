package net.ashpapi.papioptimization.core;

import java.util.concurrent.atomic.AtomicInteger;

public final class PapiRecipeEpoch {

    private static final AtomicInteger EPOCH = new AtomicInteger(1);

    private PapiRecipeEpoch() {}

    public static int current() {
        return EPOCH.get();
    }

    public static void bump() {
        EPOCH.incrementAndGet();
    }
}
