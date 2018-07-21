package com.winthier.shutdown.bukkit;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import org.bukkit.scheduler.BukkitRunnable;

final class BukkitShutdownTask {
    private final BukkitShutdownPlugin plugin;
    private BukkitRunnable task;
    @Getter
    private int seconds;
    private List<Integer> broadcastTimes = Arrays.asList(60 * 60, 60 * 30, 60 * 15, 60 * 10, 60 * 5, 60, 45, 30, 20, 10, 5);
    private List<Integer> titleTimes = Arrays.asList(600, 30, 5);

    BukkitShutdownTask(BukkitShutdownPlugin plugin, int seconds) {
        this.plugin = plugin;
        this.seconds = seconds;
    }

    void start() {
        task = new BukkitRunnable() {
            @Override public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, BukkitShutdownPlugin.TICKS_PER_SECOND, BukkitShutdownPlugin.TICKS_PER_SECOND);
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
            if (broadcastTimes.contains(seconds)) plugin.broadcastShutdown(seconds);
            if (titleTimes.contains(seconds)) plugin.titleShutdown(seconds);
            if (seconds == 0) {
                plugin.shutdownNow();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        seconds -= 1;
    }
}
