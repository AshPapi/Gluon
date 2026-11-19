package net.ashpapi.papioptimization.server;

import net.ashpapi.papioptimization.config.PapiConfig;

public final class PapiMsptController {

    private static final int WINDOW = 100;
    private static final long[] RING = new long[WINDOW];
    private static int cursor = 0;
    private static int samples = 0;
    private static long sumNanos = 0L;
    private static volatile double averageMspt = 0.0;

    private PapiMsptController() {}

    public static synchronized void recordTickNanos(long tickNanos) {
        if (tickNanos < 0L) {
            return;
        }
        if (samples < WINDOW) {
            RING[cursor] = tickNanos;
            sumNanos += tickNanos;
            samples++;
        } else {
            sumNanos -= RING[cursor];
            RING[cursor] = tickNanos;
            sumNanos += tickNanos;
        }
        cursor = (cursor + 1) % WINDOW;
        averageMspt = ((double) sumNanos / (double) samples) * 1.0E-6;
    }

    public static synchronized void reset() {
        java.util.Arrays.fill(RING, 0L);
        cursor = 0;
        samples = 0;
        sumNanos = 0L;
        averageMspt = 0.0;
    }

    public static double getAverageMspt() {
        return averageMspt;
    }

    public static int getAiThrottleInterval() {
        double mspt = averageMspt;
        double target = PapiConfig.msptTarget();
        if (mspt >= target + 3.0) {
            return 16;
        }
        if (mspt >= target - 3.0) {
            return 12;
        }
        if (mspt >= target - 10.0) {
            return 8;
        }
        if (mspt >= target - 15.0) {
            return 4;
        }
        return 1;
    }

    public static int getVillagerLobotomyInterval() {
        double mspt = averageMspt;
        double target = PapiConfig.msptTarget();
        if (mspt >= target + 3.0) {
            return 20;
        }
        if (mspt >= target - 3.0) {
            return 12;
        }
        if (mspt >= target - 10.0) {
            return 8;
        }
        if (mspt >= target - 15.0) {
            return 4;
        }
        return 1;
    }

    public static int getAutosaveBatchSize(int queueSize) {
        double mspt = averageMspt;
        double target = PapiConfig.msptTarget();
        if (mspt >= target + 1.0) {
            return Math.max(4, queueSize / 80);
        }
        if (mspt >= target - 10.0) {
            return Math.max(12, queueSize / 50);
        }
        return Math.max(16, queueSize / 35);
    }
}
