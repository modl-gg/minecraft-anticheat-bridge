package gg.modl.bridge.command;

import gg.modl.bridge.config.BridgeConfig;
import gg.modl.bridge.detection.ViolationRecord;
import gg.modl.bridge.detection.ViolationTracker;
import gg.modl.bridge.http.BridgeHttpClient;
import gg.modl.bridge.http.request.CreateTicketRequest;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class AnticheatReportCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final BridgeHttpClient httpClient;
    private final ViolationTracker violationTracker;

    public AnticheatReportCommand(JavaPlugin plugin, BridgeConfig config, BridgeHttpClient httpClient, ViolationTracker violationTracker) {
        this.plugin = plugin;
        this.config = config;
        this.httpClient = httpClient;
        this.violationTracker = violationTracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            sender.sendMessage("[ModlBridge] This command can only be executed from console.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("Usage: /anticheat-report <player>");
            return true;
        }

        String playerName = args[0];

        // Resolve player UUID
        Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        UUID targetUuid;
        if (onlinePlayer != null) {
            targetUuid = onlinePlayer.getUniqueId();
            playerName = onlinePlayer.getName();
        } else {
            @SuppressWarnings("deprecation")
            UUID offlineUuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();
            targetUuid = offlineUuid;
        }

        List<ViolationRecord> records = violationTracker.getRecords(targetUuid);

        if (records.isEmpty()) {
            sender.sendMessage("[ModlBridge] No recent violations found for " + playerName);
            return true;
        }

        String subject = "Anticheat Report - " + playerName + " (" + records.size() + " violations)";

        String description = "Automated anticheat report generated via /anticheat-report command.\n\n" +
                "Player: " + playerName + "\n" +
                "Records: " + records.size() + " violation(s)\n\n" +
                "Recent violations:\n" +
                records.stream()
                        .map(ViolationRecord::toString)
                        .collect(Collectors.joining("\n"));

        List<String> tags = List.of("anticheat", "report");

        CreateTicketRequest request = new CreateTicketRequest(
                targetUuid.toString(),
                config.getIssuerName(),
                "REPORT",
                subject,
                description,
                targetUuid.toString(),
                playerName,
                tags,
                "NORMAL",
                config.getServerName()
        );

        String finalPlayerName = playerName;
        plugin.getLogger().info("[ModlBridge] Creating anticheat report for " + playerName + " with " + records.size() + " violation(s)");

        httpClient.createTicket(request).thenAccept(response -> {
            if (response.isSuccess()) {
                plugin.getLogger().info("[ModlBridge] Report created for " + finalPlayerName + " - Ticket: " + response.getTicketId());
            } else {
                plugin.getLogger().warning("[ModlBridge] Failed to create report for " + finalPlayerName + ": " + response.getMessage());
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("[ModlBridge] Error creating report for " + finalPlayerName + ": " + throwable.getMessage());
            return null;
        });

        return true;
    }
}
