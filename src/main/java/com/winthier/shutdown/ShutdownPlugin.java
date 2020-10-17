package com.winthier.shutdown;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class ShutdownPlugin extends JavaPlugin implements Listener {
    // Configuration
    private long maxLagTime;
    private long lowMemThreshold;
    private long maxLowMemTime;
    private long maxEmptyTime;
    private long maxUptime;
    private long minUptime;
    private long lagShutdownTime;
    private long lowMemShutdownTime;
    private long uptimeShutdownTime;
    private double lagThreshold;
    private boolean timingsReport;
    private List<Long> shutdownBroadcastTimes;
    private List<Long> shutdownTitleTimes;
    private Map<String, String> messages;
    // State
    private TPS tps;
    private long uptime;
    private long lagTime;
    private long lowMemTime;
    private long emptyTime;
    private ShutdownTask shutdownTask = null;

    enum ShutdownReason {
        MANUAL("Manual shutdown"),
        LAG("Server lag"),
        LOWMEM("Low memory"),
        UPTIME("Uptime too long"),
        EMPTY("Server is empty");

        public final String human;

        ShutdownReason(final String human) {
            this.human = human;
        }
    }

    // --- Setup routines

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configure();
        getServer().getPluginManager().registerEvents(this, this);
        long minute = 20 * 60;
        tps = new TPS(minute);
        getServer().getScheduler().runTaskTimer(this, tps, minute, minute);
        getServer().getScheduler().runTaskTimer(this, () -> minutePassed(), minute, minute);
    }

    void configure() {
        reloadConfig();
        maxLagTime = getConfig().getLong("MaxLagTime");
        lagThreshold = getConfig().getDouble("LagThreshold");
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
        messages = new HashMap<>();
        ConfigurationSection section = getConfig().getConfigurationSection("Messages");
        for (String key: section.getKeys(false)) {
            messages.put(key, section.getString(key));
        }
    }

    // --- Command interface

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            switch (args[0]) {
            case "info":
                sender.sendMessage("§6§lShutdown Info");
                sender.sendMessage(String.format("Uptime: §e%s §7/ %s", infoMinutes(uptime), maxUptime < 0 ? "~" : infoMinutes(maxUptime)));
                sender.sendMessage(String.format("TPS: §e%.2f §6/ %.2f §8|§7 %s / %s",
                                                 tps.tps(), lagThreshold, infoMinutes(lagTime), maxLagTime < 0 ? "~" : infoMinutes(maxLagTime)));
                sender.sendMessage(String.format("Free: §e%d §6/ %d MiB §8|§7 %s / %s",
                                                 freeMem(), lowMemThreshold, infoMinutes(lowMemTime), maxLowMemTime < 0 ? "~" : infoMinutes(maxLowMemTime)));
                sender.sendMessage(String.format("Empty: §e%s §8|§7 %s / %s",
                                                 getServer().getOnlinePlayers().isEmpty() ? "yes" : "no",
                                                 infoMinutes(emptyTime), maxEmptyTime < 0 ? "~" : infoMinutes(maxEmptyTime)));
                if (!isShutdownActive()) {
                    sender.sendMessage("§aNo shutdown active");
                } else {
                    sender.sendMessage(String.format("§cShutdown active: §7%d seconds left.", shutdownTask.getSeconds()));
                }
                return true;
            case "reload":
                configure();
                lagTime = 0;
                lowMemTime = 0;
                emptyTime = 0;
                sender.sendMessage("§eShutdown configuration reloaded.");
                return true;
            case "reset":
                lagTime = 0;
                lowMemTime = 0;
                emptyTime = 0;
                sender.sendMessage("§eTimings were reset.");
                return true;
            case "cancel":
                if (!isShutdownActive()) {
                    sender.sendMessage("§cThere is no shutdown going on.");
                    return true;
                }
                cancelShutdown();
                sender.sendMessage("§eShutdown cancelled");
                return true;
            case "now":
                if (isShutdownActive()) {
                    sender.sendMessage("§cThere is already a shutdown active");
                    return true;
                }
                shutdown(30, ShutdownReason.MANUAL);
                sender.sendMessage("§eShutdown triggered in 30 seconds");
                return true;
            case "dump":
                dumpAllThreads();
                sender.sendMessage("Threads dumped");
                return true;
            default:
                long seconds = 30;
                try {
                    seconds = Long.parseLong(args[0]);
                } catch (NumberFormatException nfe) {
                    return false;
                }
                if (isShutdownActive()) {
                    sender.sendMessage("§cThere is already a shutdown active");
                    return true;
                }
                if (seconds < 0) return false;
                shutdown(seconds, ShutdownReason.MANUAL);
                sender.sendMessage(String.format("§eShutdown triggered in %d seconds", seconds));
                return true;
            }
        }
        return false;
    }

    // --- Tick timer

    /**
     * Called once per minute by scheduler created in onEnable().
     */
    private void minutePassed() {
        uptime += 1;
        // Fast returns
        if (isShutdownActive()) return;
        if (uptime < minUptime) return;
        // Lag time
        if (tps.tps() < lagThreshold) {
            getServer().broadcast("§e[Shutdown] " + "§cTPS is at " + String.format("%.2f", tps.tps()), "shutdown.alert");
            if (lagTime == 0 && timingsReport) {
                getLogger().info("Triggering timings report");
                getServer().dispatchCommand(getServer().getConsoleSender(), "timings report");
            }
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
            getServer().broadcast("§e[Shutdown] " + "§cFree memory is at " + free + " MiB", "shutdown.alert");
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
            }
        }
        // Max uptime
        if (maxUptime >= 0 && uptime > maxUptime) {
            shutdown(uptimeShutdownTime, ShutdownReason.UPTIME);
            return;
        }
    }

    static long freeMem() {
        Runtime rt = Runtime.getRuntime();
        return (rt.freeMemory() - rt.totalMemory() + rt.maxMemory()) >> 20;
    }

    // --- Event Handler

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        emptyTime = 0;
    }

    // --- Shutdown triggers

    boolean isShutdownActive() {
        return shutdownTask != null;
    }

    boolean cancelShutdown() {
        if (shutdownTask == null) return false;
        shutdownTask.stop();
        shutdownTask = null;
        return true;
    }

    boolean shutdown(long seconds, ShutdownReason reason) {
        if (shutdownTask != null) return false;
        if (seconds > 0 && timingsReport) {
            getLogger().info("Triggering timings report");
            getServer().dispatchCommand(getServer().getConsoleSender(), "timings report");
        }
        getServer().broadcast(String.format("§eInitiating shutdown in %d seconds. Reason: %s", seconds, reason.human), "shutdown.alert");
        getLogger().info(String.format("Initiating shutdown in %d seconds. Reason: %s", seconds, reason.human));
        shutdownTask = new ShutdownTask(this, seconds);
        shutdownTask.start();
        return true;
    }

    void shutdownNow() {
        String msg = getMessage("Kick");
        for (Player player : getServer().getOnlinePlayers()) {
            player.kickPlayer(msg);
        }
        getServer().shutdown();
    }

    // --- Messaging

    void broadcastShutdown(long seconds) {
        getServer().broadcast(getMessage("Broadcast", seconds), "shutdown.notify");
    }

    void titleShutdown(long seconds) {
        for (Player player: getServer().getOnlinePlayers()) {
            if (player.hasPermission("shutdown.notify")) {
                player.sendTitle(getMessage("Title", seconds), getMessage("Subtitle", seconds));
            }
        }
    }

    String getMessage(String key) {
        String result = messages.get(key);
        return result != null ? result : "";
    }

    String getMessage(String key, long seconds) {
        String result = messages.get(key);
        if (result == null) return "";
        result = result.replace("{time}", formatSeconds(seconds));
        return result;
    }

    /**
     * Format a number of seconds in a nice human readable way,
     * utilizing some of the messages from the config.yml.
     */
    String formatSeconds(long seconds) {
        if (seconds == 1L) {
            return "1 " + messages.get("Second");
        } else if (seconds < 60L) {
            return String.format("%d %s", seconds, messages.get("Seconds"));
        } else if (seconds == 60L) {
            return "1 " + messages.get("Minute");
        } else if (seconds == 3600L) {
            return "1 " + messages.get("Hour");
        } else if (seconds % 60L == 0L) {
            return String.format("%d %s", seconds / 60L, messages.get("Minutes"));
        } else {
            return String.format("%02d:%02d %s", seconds / 60L, seconds % 60L, messages.get("Minutes"));
        }
    }

    /**
     * Format a number of seconds informationally with colons.
     */
    static String infoMinutes(long minutes) {
        long hours = minutes / 60L;
        long days = hours / 24L;
        if (days > 0) return String.format("%dD.%02d:%02d", days, hours % 24, minutes % 60L);
        return String.format("%02d:%02d", hours, minutes % 60L);
    }

    void dumpAllThreads() {
        getLogger().info("Dumping all threads.");
        Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();
        getLogger().info(map.size() + " threads.");
        ThreadMXBean tmxb = ManagementFactory.getThreadMXBean();
        for (Map.Entry<Thread, StackTraceElement[]> entry: map.entrySet()) {
            Thread thread = entry.getKey();
            long cpuTime = tmxb.getThreadCpuTime(thread.getId()) / 1000000000;
            StackTraceElement[] trace = entry.getValue();
            getLogger().info("Thread " + thread.getId()
                             + " name=" + thread.getName()
                             + " prio=" + thread.getPriority()
                             + " state=" + thread.getState()
                             + " cputime=" + cpuTime + "s");
            for (int i = 0; i < trace.length; i += 1) {
                getLogger().info(i + ") " + trace[i]);
            }
            getLogger().info("");
        }
    }
}
