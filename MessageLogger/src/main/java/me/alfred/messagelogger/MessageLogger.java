package me.alfred.messagelogger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessageLogger extends JavaPlugin implements Listener {

    private File logFolder;
    private ExecutorService executor;
    private BukkitScheduler scheduler;
    private DataBaseManager databaseManager;

    @Override
    public void onEnable() {
        logFolder = new File(getDataFolder(), "MessageLogs");
        if (!logFolder.exists() && !logFolder.mkdirs()) {
            getLogger().warning("Failed to create log directory!");
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        executor = Executors.newCachedThreadPool();
        scheduler = Bukkit.getServer().getScheduler();

        String dbUrl = "jdbc:mysql://localhost:3306/logger";
        String dbUsername = "root";
        String dbPassword = "Alfred123!";
        databaseManager = new DataBaseManager(dbUrl, dbUsername, dbPassword);
        databaseManager.initializeDatabase();
    }

    @Override
    public void onDisable() {
        executor.shutdown();
        databaseManager.close();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("mlogs")) {
            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /mlogs <player> [page]");
                return true;
            }

            String playerName = args[0];
            int page = 1;

            if (args.length > 1) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid page number!");
                    return true;
                }
            }

            // Retrieve logs from database instead of file
            int finalPage = page;
            CompletableFuture.runAsync(() -> {
                List<String> logLines = databaseManager.getLogMessages(playerName);
                int logsPerPage = 10;
                int totalPages = (int) Math.ceil((double) logLines.size() / logsPerPage);

                int currentPage = finalPage;

                if (currentPage < 1 || currentPage > totalPages) {
                    currentPage = totalPages;
                }

                int finalCurrentPage = currentPage;
                scheduler.runTask(this, () -> sender.sendMessage(ChatColor.GOLD + "Message Logs for " + playerName + " (Page " + finalCurrentPage + "/" + totalPages + "):"));

                int startIndex = (currentPage - 1) * logsPerPage;
                int endIndex = Math.min(startIndex + logsPerPage, logLines.size());

                for (int i = startIndex; i < endIndex; i++) {
                    String logMessage = formatLogMessage(logLines.get(i));
                    scheduler.runTask(this, () -> sender.sendMessage(logMessage));

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }, executor);

            return true;
        }

        return false;
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        CompletableFuture.runAsync(() -> {
            logMessage(player, "Chat", message);
            databaseManager.insertLog(player.getName(), "Chat", message);
        }, executor);
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().substring(1);
        CompletableFuture.runAsync(() -> {
            logMessage(player, "Command", command);
            databaseManager.insertLog(player.getName(), "Command", command);
        }, executor);
    }

    private void logMessage(Player player, String messageType, String message) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = dateFormat.format(new Date());

        String playerName = player.getName().trim();
        playerName = playerName.replaceAll("[^a-zA-Z0-9_]", "_");

        final String logFileName = playerName + ".txt";
        final File logFile = new File(logFolder, logFileName);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            String formattedMessage;
            if (messageType.equalsIgnoreCase("Command")) {
                formattedMessage = "[" + timestamp + "] " + formatPlayerName(playerName) +
                        ": " + ChatColor.GREEN + "/" + message + ChatColor.RESET + "\n";
            } else {
                formattedMessage = "[" + timestamp + "] " + formatPlayerName(playerName) +
                        ": " + formatMessage(message) + "\n";
            }
            writer.write(formattedMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String formatMessage(String message) {
        return ChatColor.YELLOW + message + ChatColor.RESET;
    }

    private String formatPlayerName(String playerName) {
        return ChatColor.BLUE + playerName + ChatColor.RESET;
    }

    private String formatLogMessage(String logMessage) {
        String[] parts = logMessage.split(" ", 3);
        if (parts.length == 3) {
            String timestamp = parts[0];
            String playerName = parts[1];
            String message = parts[2];

            return ChatColor.GRAY + "[" + timestamp + "] " + formatPlayerName(playerName) +
                    ChatColor.GRAY + ": " + formatMessage(message);
        } else {
            return logMessage;
        }
    }
}
