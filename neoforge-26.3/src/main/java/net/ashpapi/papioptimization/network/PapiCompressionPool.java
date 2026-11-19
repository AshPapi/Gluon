package net.ashpapi.papioptimization.network;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class PapiCompressionPool {

    private static final int MAX_POOL_SIZE = 32;
    private static final int DEFLATER_LEVEL = 4;

    private static final ConcurrentLinkedQueue<Deflater> DEFLATERS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger DEFLATER_COUNT = new AtomicInteger(0);

    private static final ConcurrentLinkedQueue<Inflater> INFLATERS = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger INFLATER_COUNT = new AtomicInteger(0);

    private PapiCompressionPool() {}

    public static Deflater acquireDeflater() {
        Deflater d = DEFLATERS.poll();
        if (d != null) {
            DEFLATER_COUNT.decrementAndGet();
            return d;
        }
        return new Deflater(DEFLATER_LEVEL);
    }

    public static void releaseDeflater(Deflater d) {
        if (d == null) {
            return;
        }
        d.reset();
        if (DEFLATER_COUNT.incrementAndGet() <= MAX_POOL_SIZE) {
            DEFLATERS.offer(d);
        } else {
            DEFLATER_COUNT.decrementAndGet();
            d.end();
        }
    }

    public static Inflater acquireInflater() {
        Inflater inf = INFLATERS.poll();
        if (inf != null) {
            INFLATER_COUNT.decrementAndGet();
            return inf;
        }
        return new Inflater();
    }

    public static void releaseInflater(Inflater inf) {
        if (inf == null) {
            return;
        }
        inf.reset();
        if (INFLATER_COUNT.incrementAndGet() <= MAX_POOL_SIZE) {
            INFLATERS.offer(inf);
        } else {
            INFLATER_COUNT.decrementAndGet();
            inf.end();
        }
    }
}
