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

    public SidebarListener enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
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

    @EventHandler
    void onPlayerSidebar(PlayerSidebarEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("shutdown.alert")) return;
        double[] tps = Bukkit.getTPS();
        String msg = ChatColor.GOLD + "TPS " + formatTPS(tps[0]) + " " + formatTPS(tps[1]) + " " + formatTPS(tps[2]);
        event.addLines(plugin, Priority.HIGHEST, Arrays.asList(msg));
    }
}
