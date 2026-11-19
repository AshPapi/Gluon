package net.ashpapi.papioptimization.client;

import java.util.concurrent.atomic.AtomicInteger;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PapiClientCaches {

    private static final AtomicInteger EPOCH = new AtomicInteger(1);

    private PapiClientCaches() {}

    public static int epoch() {
        return EPOCH.get();
    }

    public static void invalidateAll() {
        EPOCH.incrementAndGet();
    }
}
