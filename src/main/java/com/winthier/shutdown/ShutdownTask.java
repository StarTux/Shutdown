package com.winthier.shutdown;

import lombok.Getter;
import org.bukkit.scheduler.BukkitRunnable;

final class ShutdownTask {
    private final ShutdownPlugin plugin;
    private BukkitRunnable task;
    @Getter private long totalSeconds;
    @Getter private long seconds; // time left
    private long started;

    ShutdownTask(final ShutdownPlugin plugin, final long seconds) {
        if (seconds < 0) throw new IllegalArgumentException("seconds < 0");
        this.plugin = plugin;
        this.seconds = seconds;
        this.totalSeconds = seconds;
        this.started = System.currentTimeMillis();
    }

    void start() {
        task = new BukkitRunnable() {
            @Override public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
    }

    void stop() {
        if (task == null) return;
        try {
            task.cancel();
        } catch (IllegalStateException ise) {
            ise.printStackTrace();
        }
        task = null;
    }

    private void tick() {
        long newSeconds = totalSeconds - ((System.currentTimeMillis() - started) / 1000L);
        if (newSeconds == seconds) return;
        seconds = newSeconds;
        try {
            if (plugin.getShutdownBroadcastTimes().contains(seconds)) {
                plugin.broadcastShutdown(seconds);
            }
            if (plugin.getShutdownTitleTimes().contains(seconds)) {
                plugin.titleShutdown(seconds);
            }
            if (seconds <= 0) {
                plugin.shutdownNow();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
