package com.yosefario.nclientv3.api.source.ehentai;

public final class EHentaiRateLimiter {
    private static final long MIN_INTERVAL_MS = 500;
    private static final long MAX_JITTER_MS = 250;
    private static final Object LOCK = new Object();
    private static long nextAllowed = 0;

    private EHentaiRateLimiter() {
    }

    public static void acquire() {
        long wait;
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            long slot = Math.max(now, nextAllowed);
            wait = slot - now;
            nextAllowed = slot + MIN_INTERVAL_MS + (long) (Math.random() * MAX_JITTER_MS);
        }
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
