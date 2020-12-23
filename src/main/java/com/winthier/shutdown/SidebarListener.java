package com.winthier.shutdown;

import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@RequiredArgsConstructor
public final class SidebarListener implements Listener {
    private final ShutdownPlugin plugin;
    String sidebarLine = null;

    public SidebarListener enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        return this;
    }

    String formatTPS(double tps) {
        ChatColor color;
        if (tps < 16) {
            color = ChatColor.RED;
        } else if (tps < 18) {
            color = ChatColor.YELLOW;
        } else {
            color = ChatColor.GREEN;
        }
        return color + String.format("%.1f", tps);
    }

    void tick() {
        double[] tps = Bukkit.getTPS();
        sidebarLine = ""
            + ChatColor.GOLD + Bukkit.getOnlinePlayers().size() + ChatColor.GRAY + "p"
            + " " + ChatColor.GOLD + formatTPS(tps[0]) + ChatColor.GRAY + "tps";
    }

    @EventHandler
    void onPlayerSidebar(PlayerSidebarEvent event) {
        if (sidebarLine == null) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("shutdown.alert")) return;
        event.addLines(plugin, Priority.HIGHEST, Arrays.asList(sidebarLine));
    }
}
