package com.winthier.shutdown.bukkit;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.simple.JSONValue;

/**
 * This is a quick and dirty implementation of the shutdown
 * plugin. A future version should be customizable and portable.
 */
public class BukkitShutdownPlugin extends JavaPlugin implements Listener {
    static enum ShutdownReason {
        NONE("This is a mistake. Please check the code"),
        MANUAL("Manual shutdown"),
        LAG("Server lag"),
        UPTIME("Uptime too long"),
        EMPTY("Server is empty"),
        ;
        public final String human;
        ShutdownReason(String human) {
            this.human = human;
        }
    }

    static final long TICKS_PER_SECOND = 20L;
    final double LAG_THRESHOLD = 17.0; // in ticks per second
    final TPS tps = new TPS(TICKS_PER_SECOND * 60L);
    int maxLagTime = 5; // in minutes
    long maxUptime = 24; // in hours
    long minUptime = 60; // in minutes
    long uptime = 0L; // in minutes
    BukkitRunnable task;
    BukkitShutdownTask shutdownTask = null;
    int lagTime = 0;
    boolean shutdownEmpty = true;
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        configure();
        tps.start(this);
        task = new BukkitRunnable() {
            @Override public void run() {
                tick();
            }
        };
        task.runTaskTimer(this, TICKS_PER_SECOND * 60L, TICKS_PER_SECOND * 60L);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
    }

    void configure()
    {
        reloadConfig();
        maxUptime = getConfig().getLong("MaxUptime", 24);
        minUptime = getConfig().getLong("MinUptime", 60);
        shutdownEmpty = getConfig().getBoolean("ShutdownEmpty", true);
        maxLagTime = getConfig().getInt("MaxLagTime", 10);
    }

    static String format(String msg, Object... args) {
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        if (args.length > 0) msg = String.format(msg, args);
        return msg;
    }

    static void send(CommandSender sender, String msg, Object... args) {
        sender.sendMessage(format(msg, args));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String firstArg = args[0].toLowerCase();
            if (firstArg.equals("info")) {
                send(sender, "&3Shutdown Info");
                send(sender, " &3Uptime: &b%d&3:&b%02d &3/ &b%d&3:&b00&3 hours", uptime / 60L, uptime % 60L, maxUptime);
                send(sender, " &3Performance: &b%.02f&3 TPS", tps.tps());
                send(sender, " &3Lag Time: &b%d&3/&b%d&3 minutes", lagTime, maxLagTime);
                if (!isShutdownActive()) {
                    send(sender, " &3No shutdown active");
                } else {
                    send(sender, " &3Shutdown active: &b%s&3 left.", formatSeconds(shutdownTask.getSeconds()));
                }
            } else if (firstArg.equals("reload")) {
                configure();
                lagTime = 0;
                send(sender, "&eShutdown configuration reloaded.");
            } else if (firstArg.equals("cancel")) {
                if (!isShutdownActive()) {
                    send(sender, "&cThere is no shutdown going on.");
                    return true;
                }
                cancelShutdown();
                send(sender, "&3Shutdown cancelled");
            } else if (firstArg.equals("now")) {
                if (isShutdownActive()) {
                    send(sender, "&4There is already a shutdown active");
                    return true;
                }
                shutdown(30, ShutdownReason.MANUAL);
                send(sender, "&3Shutdown triggered in 30 seconds");
            } else {
                if (isShutdownActive()) {
                    send(sender, "&cThere is already a shutdown active");
                    return true;
                }
                int seconds = 30;
                try {
                    seconds = Integer.parseInt(firstArg);
                } catch (NumberFormatException nfe) {
                    return false;
                }
                if (seconds < 0) return false;
                shutdown(seconds, ShutdownReason.MANUAL);
                send(sender, "Shutdown triggered in %d seconds", seconds);
            }
        } else if (args.length == 2) {
            String firstArg = args[0].toLowerCase();
            if (firstArg.equals("uptime")) {
                long newUptime = uptime;
                try {
                    newUptime = Long.parseLong(args[1]);
                } catch (NumberFormatException nfe) {
                    return false;
                }
                if (newUptime < 0L) return false;
                uptime = newUptime;
                send(sender, "&3Uptime set to %s.", formatSeconds((int)newUptime * 60));
            }
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void onDisable() {
        tps.stop();
        task.cancel();
        task = null;
    }

    /**
     * Call this once per minute.
     */
    void tick() {
        if (!isShutdownActive() && uptime > minUptime) {
            if (tps.tps() < LAG_THRESHOLD) {
                lagTime += 1;
                if (lagTime > maxLagTime) {
                    shutdown(30, ShutdownReason.LAG);
                }
            } else {
                lagTime = 0;
            }
            if (uptime > maxUptime * 60) {
                shutdown(60, ShutdownReason.UPTIME);
            }
        }
        uptime += 1;
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (uptime <= minUptime) return;
        if (!shutdownEmpty) return;
        int playerCount = getServer().getOnlinePlayers().size();
        //System.out.println("Player count on quit: " + playerCount); // TODO remove
        if (playerCount > 1) return;
        shutdown(0, ShutdownReason.EMPTY);
    }

    boolean isShutdownActive() {
        return shutdownTask != null;
    }

    boolean cancelShutdown() {
        if (shutdownTask == null) return false;
        shutdownTask.stop();
        shutdownTask = null;
        return true;
    }

    boolean shutdown(int seconds, ShutdownReason reason) {
        if (shutdownTask != null) return false;
        getLogger().info(String.format("Initiating shutdown in %d seconds. Reason: %s", seconds, reason.human));
        shutdownTask = new BukkitShutdownTask(this, seconds);
        shutdownTask.start();
        return true;
    }

    void sendToServer(Player player, String serverName) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        try {
            out.writeUTF("Connect");
            out.writeUTF(serverName);
        } catch (IOException ex) {
            // Impossible(?)
        }
        System.out.println("Sending " + player.getName() + " to " + serverName);
        player.sendPluginMessage(this, "BungeeCord", b.toByteArray());
    }

    void sendAllPlayersToHub() {
        for (Player player : getServer().getOnlinePlayers()) {
            sendToServer(player, "hub");
        }
    }

    void kickAllPlayers() {
        for (Player player : getServer().getOnlinePlayers()) {
            player.kickPlayer("Quick restart");
        }
    }

    void shutdownNow() {
        for (Player player: getServer().getOnlinePlayers()) {
            player.kickPlayer("Quick restart");
        }
        getServer().shutdown();
    }

    static String formatSeconds(int seconds) {
        if (seconds == 1) {
            return "1 second";
        } else if (seconds < 60) {
            return String.format("%d seconds", seconds);
        } else if (seconds == 60) {
            return "1 minute";
        } else if (seconds == 60 * 60) {
            return "1 hour";
        } else if (seconds % 60 == 0) {
            return String.format("%d minutes", seconds / 60);
        } else {
            return String.format("%02d:%02d minutes", seconds / 60, seconds % 60);
        }
    }

    void broadcastShutdown(int seconds) {
        String message = format("&3Quick restart in %s.", formatSeconds(seconds));
        for (Player player : getServer().getOnlinePlayers()) {
            player.sendMessage(message);
        }
    }

    void titleShutdown(int seconds) {
        for (Player player: getServer().getOnlinePlayers()) {
            player.sendTitle(format("&3Quick Restart"), format("&3in %s", formatSeconds(seconds)));
        }
    }
}
