package com.winthier.shutdown;

final class TPS implements Runnable {
    private long interval; // in ticks
    private long time;
    private double tps = 20;

    TPS(final long interval) {
        this.interval = interval;
        this.time = System.nanoTime();
    }

    @Override
    public void run() {
        long now = System.nanoTime();
        long nanos = now - time;
        tps = ((double) interval * 1000000000) / (double) nanos;
        time = now;
    }

    double tps() {
        return tps;
    }
}
