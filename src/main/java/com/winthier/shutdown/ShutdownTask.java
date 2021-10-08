package com.winthier.shutdown;

import lombok.Getter;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

final class ShutdownTask {
    private final ShutdownPlugin plugin;
    private BukkitRunnable task;
    private long totalSeconds;
    @Getter private long seconds;
    private BossBar bossBar;
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
        bossBar = BossBar.bossBar(plugin.getMessage(MessageType.BOSS_BAR, seconds), 1.0f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_20);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(bossBar);
        }
    }

    void stop() {
        if (task == null) return;
        try {
            task.cancel();
        } catch (IllegalStateException ise) {
            ise.printStackTrace();
        }
        task = null;
        if (bossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(bossBar);
            }
            bossBar = null;
        }
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
        if (bossBar != null) {
            bossBar.name(plugin.getMessage(MessageType.BOSS_BAR, seconds));
            bossBar.progress(Math.min(1.0f, Math.max(0.0f, (float) seconds / (float) totalSeconds)));
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showBossBar(bossBar);
            }
        }
    }
}
