package lol.oddballs.EssentialEconomy.commands.money;

import lol.oddballs.EssentialEconomy.EconomyMain;
import lol.oddballs.EssentialEconomy.utils.ChatManager;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoneyTakeCommand {

    private final EconomyMain plugin;
    private final ChatManager chatManager;

    public MoneyTakeCommand(EconomyMain plugin) {
        this.plugin = plugin;
        this.chatManager = plugin.getChatManager();
    }

    public void execute(CommandSender sender, String[] args) {
        if (args.length != 3) {
            chatManager.sendConfigMessage(sender, "money.take.usage");
            return;
        }

        double amount;
        try {
            amount = plugin.getAmountFromString(args[2]);
        } catch (NumberFormatException e) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%amount%", args[2]);
            chatManager.sendConfigMessage(sender, "money.take.invalidAmount", placeholders);
            return;
        }

        if (!Double.isFinite(amount) || amount <= 0) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%amount%", args[2]);
            chatManager.sendConfigMessage(sender, "money.take.invalidAmount", placeholders);
            return;
        }

        List<OfflinePlayer> targets = plugin.getPlayersFromString(sender, args[1]);
        if (targets.isEmpty()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%player%", args[1]);
            chatManager.sendConfigMessage(sender, "money.take.otherDoesntExist", placeholders);
            return;
        }

        String formattedAmount = EconomyMain.format(amount);

        if (args[1].equalsIgnoreCase("@a")) {
            int count = 0;
            for (OfflinePlayer target : targets) {
                if (!plugin.getEco().hasAccount(target.getUniqueId())) {
                    continue;
                }
                if (plugin.getEco().has(target.getUniqueId(), amount) && plugin.getEco().withdraw(target.getUniqueId(), amount)) {
                    count++;
                    if (target.isOnline() && target.getPlayer() != null) {
                        Map<String, String> targetPlaceholders = new HashMap<>();
                        targetPlaceholders.put("%amount%", formattedAmount);
                        chatManager.sendConfigMessage(target.getPlayer(), "money.take.taken", targetPlaceholders);
                    }
                }
            }
            Map<String, String> senderPlaceholders = new HashMap<>();
            senderPlaceholders.put("%total%", String.valueOf(count));
            senderPlaceholders.put("%amount%", formattedAmount);
            chatManager.sendConfigMessage(sender, "money.take.takeMultiple", senderPlaceholders);
            return;
        }

        OfflinePlayer target = targets.get(0);
        String targetName = target.getName() != null ? target.getName() : args[1];

        if (!plugin.getEco().hasAccount(target.getUniqueId())) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%player%", targetName);
            chatManager.sendConfigMessage(sender, "money.take.otherNoAccount", placeholders);
            return;
        }

        if (!plugin.getEco().has(target.getUniqueId(), amount) || !plugin.getEco().withdraw(target.getUniqueId(), amount)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%player%", targetName);
            chatManager.sendConfigMessage(sender, "money.take.insufficientFunds", placeholders);
            return;
        }

        Map<String, String> senderPlaceholders = new HashMap<>();
        senderPlaceholders.put("%player%", targetName);
        senderPlaceholders.put("%amount%", formattedAmount);
        chatManager.sendConfigMessage(sender, "money.take.take", senderPlaceholders);

        if (target.isOnline()) {
            Player onlineTarget = target.getPlayer();
            if (onlineTarget != null) {
                Map<String, String> targetPlaceholders = new HashMap<>();
                targetPlaceholders.put("%amount%", formattedAmount);
                chatManager.sendConfigMessage(onlineTarget, "money.take.taken", targetPlaceholders);
            }
        }
    }
}
