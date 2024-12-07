package com.winthier.shutdown;

import com.cavetale.core.bungee.Bungee;
import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.winthier.connect.Redis;
import com.winthier.shutdown.event.ShutdownTriggerEvent;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.Getter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextDecoration.*;

@Getter
public final class ShutdownPlugin extends JavaPlugin implements Listener {
    // Configuration
    private long maxLagTime;
    private long lowMemThreshold;
    private long maxLowMemTime;
    private long maxEmptyTime;
    private long maxUptime;
    private long minUptime;
    private long scheduledShutdownTime;
    private long lagShutdownTime;
    private long lowMemShutdownTime;
    private long uptimeShutdownTime;
    private double lagThreshold;
    private boolean timingsReport;
    private List<Long> shutdownBroadcastTimes;
    private List<Long> shutdownTitleTimes;
    private List<TimeOfDay> scheduled;
    // State
    private long uptime;
    private long lagTime;
    private long lowMemTime;
    private long emptyTime;
    private ShutdownTask shutdownTask = null;
    private volatile boolean shuttingDown = false;
    private long lastTime;
    private double tps = 20.0;
    private Calendar calendar;
    private TimeOfDay lastTimeOfDay;
    private boolean whenEmpty;
    private boolean never;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configure();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::tick, 1L, 1L);
        lastTime = System.currentTimeMillis();
        calendar = new GregorianCalendar();
    }

    @Override
    public void onDisable() {
        if (shutdownTask != null) {
            shutdownTask.stop();
            shutdownTask = null;
        }
    }

    protected void configure() {
        reloadConfig();
        scheduled = new ArrayList<>();
        for (String it : getConfig().getStringList("Scheduled")) {
            try {
                scheduled.add(TimeOfDay.parse(it));
            } catch (Exception e) {
                getLogger().warning("config.yml: Invalid time of day: '" + it + "'");
            }
        }
        maxLagTime = getConfig().getLong("MaxLagTime");
        lagThreshold = getConfig().getDouble("LagThreshold");
        scheduledShutdownTime = getConfig().getLong("ScheduledShutdownTime");
        lagShutdownTime = getConfig().getLong("LagShutdownTime");
        maxLowMemTime = getConfig().getLong("MaxLowMemTime");
        lowMemThreshold = getConfig().getLong("LowMemThreshold");
        lowMemShutdownTime = getConfig().getLong("LowMemShutdownTime");
        maxEmptyTime = getConfig().getLong("MaxEmptyTime");
        maxUptime = getConfig().getLong("MaxUptime");
        minUptime = getConfig().getLong("MinUptime");
        uptimeShutdownTime = getConfig().getLong("MaxUptimeShutdownTime");
        shutdownBroadcastTimes = getConfig().getLongList("ShutdownBroadcastTimes");
        shutdownTitleTimes = getConfig().getLongList("ShutdownTitleTimes");
        timingsReport = getConfig().getBoolean("TimingsReport");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            switch (args[0]) {
            case "info":
                sender.sendMessage(text("Shutdown Info", GOLD, BOLD));
                sender.sendMessage(textOfChildren(text("Time "),
                                                  text(lastTimeOfDay.toString(), YELLOW),
                                                  text(" / " + scheduled.toString(), GRAY)));
                sender.sendMessage(textOfChildren(text("Uptime: "),
                                                  text(infoMinutes(uptime), YELLOW),
                                                  text(" / " + (maxUptime < 0 ? "~" : infoMinutes(maxUptime)), GRAY)));
                sender.sendMessage(textOfChildren(text("TPS: "),
                                                  text(String.format("%.2f", tps), YELLOW),
                                                  text(String.format(" / %.2f", lagThreshold), GOLD),
                                                  text(" | ", DARK_GRAY),
                                                  text(infoMinutes(lagTime) + " / " + (maxLagTime < 0 ? "~" : infoMinutes(maxLagTime)), GRAY)));
                sender.sendMessage(textOfChildren(text("Free: "),
                                                  text(freeMem(), YELLOW),
                                                  text(" / " + lowMemThreshold + " MiB", GOLD),
                                                  text(" | ", DARK_GRAY),
                                                  text(infoMinutes(lowMemTime) + " / " + (maxLowMemTime < 0 ? "~" : infoMinutes(maxLowMemTime)), GRAY)));
                sender.sendMessage(textOfChildren(text("Empty: "),
                                                  (getServer().getOnlinePlayers().isEmpty()
                                                   ? text("yes", YELLOW)
                                                   : text("no", DARK_GRAY)),
                                                  text(" | ", DARK_GRAY),
                                                  text(infoMinutes(emptyTime) + " / " + (maxEmptyTime < 0 ? "~" : infoMinutes(maxEmptyTime)), GRAY)));
                if (!isShutdownActive()) {
                    sender.sendMessage(text("No shutdown active", GREEN));
                } else {
                    sender.sendMessage(textOfChildren(text("Shutdown active: ", RED),
                                                      text(shutdownTask.getSeconds() + " seconds left", GRAY)));
                }
                return true;
            case "reload":
                configure();
                lagTime = 0;
                lowMemTime = 0;
                emptyTime = 0;
                lastTimeOfDay = null;
                sender.sendMessage(text("Shutdown configuration reloaded", YELLOW));
                return true;
            case "reset":
                lagTime = 0;
                lowMemTime = 0;
                emptyTime = 0;
                sender.sendMessage(text("Timings were reset", YELLOW));
                return true;
            case "cancel":
                if (!isShutdownActive()) {
                    sender.sendMessage(text("There is no shutdown going on", RED));
                    return true;
                }
                cancelShutdown();
                sender.sendMessage(text("Shutdown cancelled", YELLOW));
                return true;
            case "now":
                if (isShutdownActive()) {
                    sender.sendMessage(text("There is already a shutdown active", RED));
                    return true;
                }
                if (shutdown(30, ShutdownReason.MANUAL)) {
                    sender.sendMessage(text("Shutdown triggered in 30 seconds", YELLOW));
                } else {
                    sender.sendMessage(text("Failed to trigger shutdown!", RED));
                }
                return true;
            case "whenempty":
                whenEmpty = !whenEmpty;
                sender.sendMessage(textOfChildren(text("Shutdown next time the server empties: ", YELLOW),
                                                  (whenEmpty ? text("Yes", GREEN) : text("No", RED))));
                if (whenEmpty && Bukkit.getOnlinePlayers().isEmpty()) {
                    shutdown(0, ShutdownReason.EMPTY);
                }
                return true;
            case "never":
                never = !never;
                sender.sendMessage(textOfChildren(text("Hold all automatic shutdowns: ", YELLOW),
                                                  (never ? text("Yes", GREEN) : text("No", RED))));
                return true;
            default:
                long seconds = 30;
                try {
                    seconds = Long.parseLong(args[0]);
                } catch (NumberFormatException nfe) {
                    return false;
                }
                if (isShutdownActive()) {
                    sender.sendMessage(text("There is already a shutdown active", RED));
                    return true;
                }
                if (seconds < 0) return false;
                if (shutdown(seconds, ShutdownReason.MANUAL)) {
                    sender.sendMessage(text("Shutdown triggered in " + seconds + " seconds", YELLOW));
                } else {
                    sender.sendMessage(text("Failed to trigger shutdown!", RED));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String lower = args[0].toLowerCase();
            return Stream.of("info", "reload", "reset", "cancel", "now", "dump", "whenempty", "never")
                .filter(s -> s.contains(lower))
                .toList();
        }
        return List.of();
    }

    private void tick() {
        tps = Bukkit.getTPS()[0];
        long now = System.currentTimeMillis();
        long interval = now - lastTime;
        if (interval < 1000L) return;
        if (!isShutdownActive()) {
            calendar.setTime(new Date(now));
            TimeOfDay timeOfDay = new TimeOfDay(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
            if (!Objects.equals(timeOfDay, lastTimeOfDay)) {
                lastTimeOfDay = timeOfDay;
                if (scheduled.contains(timeOfDay)) {
                    shutdown(scheduledShutdownTime, ShutdownReason.SCHEDULED);
                }
            }
        }
        if (interval < 60L * 1000L) return;
        lastTime = now;
        minutePassed();
    }

    /**
     * Called once per minute by tick().
     */
    private void minutePassed() {
        uptime += 1;
        // Fast returns
        if (isShutdownActive()) return;
        if (uptime < minUptime) return;
        // Lag time
        if (tps < lagThreshold) {
            getServer().broadcast(text("[Shutdown] TPS is at " + String.format("%.2f", tps),
                                       RED), "shutdown.alert");
            lagTime += 1;
            if (maxLagTime >= 0 && lagTime > maxLagTime) {
                shutdown(lagShutdownTime, ShutdownReason.LAG);
                return;
            }
        } else {
            lagTime = 0;
        }
        // Low Mem
        long free = freeMem();
        if (free < lowMemThreshold) {
            getServer().broadcast(text("[Shutdown] Free memory is at " + free + " MiB", RED), "shutdown.alert");
            lowMemTime += 1;
            if (maxLowMemTime >= 0 && lowMemTime > maxLowMemTime) {
                shutdown(lowMemShutdownTime, ShutdownReason.LOWMEM);
                return;
            }
        } else {
            lowMemTime = 0;
        }
        // Server empty
        int count = getServer().getOnlinePlayers().size();
        if (count == 0) {
            emptyTime += 1;
            if (maxEmptyTime >= 0 && emptyTime > maxEmptyTime) {
                shutdown(0, ShutdownReason.EMPTY);
            } else if (whenEmpty) {
                shutdown(0, ShutdownReason.EMPTY);
            }
        }
        // Max uptime
        if (maxUptime >= 0 && uptime > maxUptime) {
            shutdown(uptimeShutdownTime, ShutdownReason.UPTIME);
            return;
        }
    }

    protected static long freeMem() {
        Runtime rt = Runtime.getRuntime();
        return (rt.freeMemory() - rt.totalMemory() + rt.maxMemory()) >> 20;
    }

    @EventHandler
    protected void onPlayerJoin(PlayerJoinEvent event) {
        emptyTime = 0;
    }

    @EventHandler
    protected void onPlayerQuit(PlayerQuitEvent event) {
        if (whenEmpty) {
            Bukkit.getScheduler().runTask(this, () -> {
                    if (Bukkit.getOnlinePlayers().isEmpty()) {
                        shutdown(0, ShutdownReason.EMPTY);
                    }
                });
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    protected void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!shuttingDown) return;
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, text("The server is restarting"));
        getLogger().info("Denying " + event.getEventName() + " due to shutdown: " + event.getPlayerProfile().getName());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    protected void onPlayerLogin(PlayerLoginEvent event) {
        if (!shuttingDown) return;
        event.disallow(PlayerLoginEvent.Result.KICK_OTHER, text("The server is restarting"));
        getLogger().info("Denying " + event.getEventName() + " due to shutdown: " + event.getPlayer().getName());
    }

    @EventHandler
    private void onPlayerHud(PlayerHudEvent event) {
        if (tps <= 19 && event.getPlayer().hasPermission("shutdown.alert")) {
            final TextColor color = tps < 16 ? RED : (tps < 18 ? YELLOW : GREEN);
            event.sidebar(PlayerHudPriority.HIGHEST,
                          List.of(textOfChildren(text(Bukkit.getOnlinePlayers().size(), color),
                                                 text("p "),
                                                 text(String.format("%.1f", tps), color),
                                                 text("tps"))));
        }
        if (shutdownTask != null) {
            PlayerHudPriority prio = shutdownTask.getSeconds() <= 30
                ? PlayerHudPriority.HIGHEST
                : PlayerHudPriority.LOWEST;
            event.bossbar(prio,
                          text("Quick restart in " + formatSeconds(shutdownTask.getSeconds()), GREEN),
                          BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_20, Set.of(),
                          (float) shutdownTask.getSeconds() / (float) shutdownTask.getTotalSeconds());
        }
    }

    protected boolean isShutdownActive() {
        return shutdownTask != null;
    }

    protected boolean cancelShutdown() {
        if (shutdownTask == null) return false;
        shutdownTask.stop();
        shutdownTask = null;
        return true;
    }

    protected boolean shutdown(long seconds, ShutdownReason reason) {
        if (never && reason != ShutdownReason.MANUAL) {
            return false;
        }
        if (timingsReport && uptime >= 60) {
            ShutdownTriggerEvent event = new ShutdownTriggerEvent(reason);
            event.callEvent();
            if (event.isCancelled()) {
                if (reason == ShutdownReason.MANUAL) {
                    getLogger().warning("Manual shutdown cancelled by " + event.getCancelledBy());
                }
                if (reason == ShutdownReason.SCHEDULED) {
                    getLogger().warning("Scheduled shutdown cancelled by " + event.getCancelledBy());
                }
                return false;
            }
            if (shutdownTask != null) return false;
            getLogger().info("Triggering timings report");
            getServer().dispatchCommand(getServer().getConsoleSender(), "timings report");
        }
        getServer().broadcast(text(String.format("Initiating shutdown in %d seconds. Reason: %s", seconds, reason.human),
                                   YELLOW), "shutdown.alert");
        getLogger().info(String.format("Initiating shutdown in %d seconds. Reason: %s", seconds, reason.human));
        shutdownTask = new ShutdownTask(this, seconds);
        shutdownTask.start();
        return true;
    }

    protected void shutdownNow() {
        getLogger().info("Shutdown is imminent");
        this.shuttingDown = true;
        NetworkServer currentServer = NetworkServer.current();
        NetworkServer targetServer = currentServer != NetworkServer.HUB
            ? NetworkServer.HUB
            : NetworkServer.VOID;
        if (targetServer != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                if (currentServer.isSurvival()) {
                    Redis.set("cavetale.server_choice." + player.getUniqueId(), NetworkServer.current().registeredName, 300L);
                }
                Bungee.send(player, targetServer.registeredName);
            }
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
                getLogger().info("Kicking all players");
                for (Player player : getServer().getOnlinePlayers()) {
                    player.kick(text("Quick restart", GREEN));
                }
            }, 10L);
        if (shutdownTask != null) {
            shutdownTask.stop();
            shutdownTask = null;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
                getLogger().info("Triggering Bukkit Shutdown");
                Bukkit.shutdown();
            }, 100L);
    }

    protected void broadcastShutdown(long seconds) {
        getServer().broadcast(text("Quick restart in " + formatSeconds(seconds), GREEN), "shutdown.notify");
    }

    protected void titleShutdown(long seconds) {
        Title title = Title.title(text("Quick restart", GREEN),
                                  text("in " + formatSeconds(seconds), GREEN));
        for (Player player: getServer().getOnlinePlayers()) {
            if (player.hasPermission("shutdown.notify")) {
                player.showTitle(title);
                player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.MASTER, 1.0f, 0.9f);
            }
        }
    }

    /**
     * Format a number of seconds in a nice human readable way,
     * utilizing some of the messages from the config.yml.
     */
    protected static String formatSeconds(long seconds) {
        if (seconds == 1L) {
            return "1 second";
        } else if (seconds < 60L) {
            return String.format("%d seconds", seconds);
        } else if (seconds == 60L) {
            return "1 minute";
        } else if (seconds == 3600L) {
            return "1 hour";
        } else if (seconds % 60L == 0L) {
            return String.format("%d minutes", seconds / 60L);
        } else {
            return String.format("%02d:%02d minutes", seconds / 60L, seconds % 60L);
        }
    }

    /**
     * Format a number of seconds informationally with colons.
     */
    protected static String infoMinutes(long minutes) {
        long hours = minutes / 60L;
        long days = hours / 24L;
        if (days > 0) return String.format("%dD.%02d:%02d", days, hours % 24, minutes % 60L);
        return String.format("%02d:%02d", hours, minutes % 60L);
    }
}
