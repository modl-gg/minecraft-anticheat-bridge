package gg.modl.bridge.command;

import gg.modl.bridge.config.BridgeConfig;
import gg.modl.bridge.http.BridgeHttpClient;
import gg.modl.bridge.http.request.CreatePunishmentRequest;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

public class AnticheatPunishCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final BridgeConfig config;
    private final BridgeHttpClient httpClient;

    public AnticheatPunishCommand(JavaPlugin plugin, BridgeConfig config, BridgeHttpClient httpClient) {
        this.plugin = plugin;
        this.config = config;
        this.httpClient = httpClient;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            sender.sendMessage("[ModlBridge] This command can only be executed from console.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("Usage: /" + label + " <player> [reason...]");
            return true;
        }

        String playerName = args[0];
        String reason = buildReason(args);

        boolean isBan = label.equalsIgnoreCase("anticheat-ban");

        // Resolve player UUID - try online player first, then use offline lookup
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

        if (isBan) {
            executeBan(targetUuid, playerName, reason);
        } else {
            executeKick(targetUuid, playerName, reason, onlinePlayer);
        }

        return true;
    }

    private void executeBan(UUID targetUuid, String playerName, String reason) {
        if (reason.isEmpty()) {
            reason = config.getDefaultPunishReason()
                    .replace("{source}", "Anticheat")
                    .replace("{check}", "Detection");
        }

        long duration = config.getCommandBanDuration();
        Long durationValue = duration < 0 ? null : duration;

        List<String> notes = List.of("Issued via /anticheat-ban console command");

        CreatePunishmentRequest request = new CreatePunishmentRequest(
                targetUuid.toString(),
                config.getIssuerName(),
                config.getCommandBanTypeOrdinal(),
                reason,
                durationValue,
                null,
                notes,
                null,
                config.getDefaultPunishSeverity(),
                "ACTIVE"
        );

        plugin.getLogger().info("[ModlBridge] Executing anticheat-ban for " + playerName + ": " + reason);

        httpClient.createPunishment(request).thenAccept(response -> {
            if (response.isSuccess()) {
                plugin.getLogger().info("[ModlBridge] Ban created for " + playerName + " - ID: " + response.getPunishmentId());
            } else {
                plugin.getLogger().warning("[ModlBridge] Failed to ban " + playerName + ": " + response.getMessage());
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("[ModlBridge] Error banning " + playerName + ": " + throwable.getMessage());
            return null;
        });
    }

    private void executeKick(UUID targetUuid, String playerName, String reason, Player onlinePlayer) {
        if (reason.isEmpty()) {
            reason = "Kicked by anticheat";
        }

        List<String> notes = List.of("Issued via /anticheat-kick console command");

        CreatePunishmentRequest request = new CreatePunishmentRequest(
                targetUuid.toString(),
                config.getIssuerName(),
                config.getCommandKickTypeOrdinal(),
                reason,
                0L,
                null,
                notes,
                null,
                config.getDefaultPunishSeverity(),
                "ACTIVE"
        );

        String finalReason = reason;
        plugin.getLogger().info("[ModlBridge] Executing anticheat-kick for " + playerName + ": " + reason);

        httpClient.createPunishment(request).thenAccept(response -> {
            if (response.isSuccess()) {
                plugin.getLogger().info("[ModlBridge] Kick punishment created for " + playerName + " - ID: " + response.getPunishmentId());
            } else {
                plugin.getLogger().warning("[ModlBridge] Failed to kick " + playerName + ": " + response.getMessage());
            }
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("[ModlBridge] Error kicking " + playerName + ": " + throwable.getMessage());
            return null;
        });

        // Also kick locally if configured and player is online
        if (config.executeKicksLocally() && onlinePlayer != null) {
            Bukkit.getScheduler().runTask(plugin, () -> onlinePlayer.kickPlayer(finalReason));
        }
    }

    private String buildReason(String[] args) {
        if (args.length <= 1) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
