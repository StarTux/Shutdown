package com.winthier.shutdown;

import com.cavetale.sidebar.PlayerSidebarEvent;
import com.cavetale.sidebar.Priority;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@RequiredArgsConstructor
public final class SidebarListener implements Listener {
    private final ShutdownPlugin plugin;
    Component sidebarLine = Component.empty();
    double tps = 20.0;

    public SidebarListener enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        return this;
    }

    private Component formatTPS() {
        NamedTextColor color;
        if (tps < 16) {
            color = NamedTextColor.RED;
        } else if (tps < 18) {
            color = NamedTextColor.YELLOW;
        } else {
            color = NamedTextColor.GREEN;
        }
        return Component.text(String.format("%.1f", tps), color);
    }

    private void tick() {
        tps = Bukkit.getTPS()[0];
        sidebarLine = Component.text().color(NamedTextColor.GOLD)
            .append(Component.text(Bukkit.getOnlinePlayers().size()))
            .append(Component.text("p", NamedTextColor.GRAY))
            .append(formatTPS())
            .append(Component.text("tps", NamedTextColor.GRAY))
            .build();
    }

    @EventHandler
    private void onPlayerSidebar(PlayerSidebarEvent event) {
        if (tps > 19) return;
        if (!event.getPlayer().hasPermission("shutdown.alert")) return;
        event.add(plugin, Priority.HIGHEST, sidebarLine);
    }
}
