package com.winthier.shutdown.bukkit;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

final class TPS {
    private BukkitRunnable task;
    private long interval; // in ticks
    private long time;
    private double tps = 20;

    TPS(long interval) {
        this.interval = interval;
    }

    void start(JavaPlugin plugin) {
        if (task != null) stop();
        task = new BukkitRunnable() {
            @Override public void run() {
                tick();
            }
        };
        time = System.currentTimeMillis();
        task.runTaskTimer(plugin, interval, interval);
    }

    void stop() {
        if (task == null) return;
        try {
            task.cancel();
        } catch (IllegalStateException ise) { }
        task = null;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        long millis = now - time;
        tps = ((double)interval * 1000.0) / (double)millis;
        time = now;
    }

    double tps() {
        return tps;
    }
}
