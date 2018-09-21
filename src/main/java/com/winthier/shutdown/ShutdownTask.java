package com.winthier.shutdown;

import lombok.Getter;
import org.bukkit.scheduler.BukkitRunnable;

final class ShutdownTask {
    private final ShutdownPlugin plugin;
    private BukkitRunnable task;
    @Getter private long seconds;

    ShutdownTask(ShutdownPlugin plugin, long seconds) {
        this.plugin = plugin;
        this.seconds = seconds;
    }

    void start() {
        task = new BukkitRunnable() {
            @Override public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, 20L, 20L);
    }

    void stop() {
        if (task == null) return;
        try {
            task.cancel();
        } catch (IllegalStateException ise) {
        }
        task = null;
    }

    private void tick() {
        try {
            if (plugin.getShutdownBroadcastTimes().contains(seconds)) plugin.broadcastShutdown(seconds);
            if (plugin.getShutdownTitleTimes().contains(seconds)) plugin.titleShutdown(seconds);
            if (seconds <= 0) {
                plugin.shutdownNow();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        seconds -= 1;
    }
}
