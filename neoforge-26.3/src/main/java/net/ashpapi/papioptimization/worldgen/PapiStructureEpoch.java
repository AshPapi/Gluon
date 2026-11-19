package net.ashpapi.papioptimization.worldgen;

import java.util.concurrent.atomic.AtomicInteger;

public final class PapiStructureEpoch {

    private static final AtomicInteger EPOCH = new AtomicInteger(1);

    private PapiStructureEpoch() {}

    public static int current() {
        return EPOCH.get();
    }

    public static void bump() {
        EPOCH.incrementAndGet();
    }
}
