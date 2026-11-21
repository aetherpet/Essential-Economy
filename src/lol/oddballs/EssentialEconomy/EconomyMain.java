package lol.oddballs.EssentialEconomy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lol.oddballs.EssentialEconomy.utils.ChatManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import lol.oddballs.EssentialEconomy.commands.BalanceCommand;
import lol.oddballs.EssentialEconomy.commands.BalanceTopCommand;
import lol.oddballs.EssentialEconomy.commands.PayCommand;
import lol.oddballs.EssentialEconomy.commands.money.MoneyCommandHandler;
import lol.oddballs.EssentialEconomy.data.ConfigHandler;
import lol.oddballs.EssentialEconomy.eco.Economy;
import lol.oddballs.EssentialEconomy.eco.SQLEconomy;
import lol.oddballs.EssentialEconomy.eco.VaultImpl;
import lol.oddballs.EssentialEconomy.eco.YamlEconomy;
import lol.oddballs.EssentialEconomy.listeners.PlayerJoinListener;
import lol.oddballs.EssentialEconomy.runnables.BalanceTopRunnable;

public class EconomyMain extends JavaPlugin {

	private MoneyCommandHandler moneyCommandHandler;
	private BalanceTopRunnable balanceTopRunnable;
	private VaultImpl vaultImpl;
	private Economy eco;
	private ChatManager chatManager;
	private ConfigHandler configHandler;
	private Map<String, String> sqlColumns = new HashMap<>();

	private static EconomyMain instance;

	@Override
	public void onEnable() {

		instance = this;
		saveDefaultConfig();
		
		configHandler = new ConfigHandler(this);
		chatManager = new ChatManager(this);

		addSqlColumns();

		if (configHandler.isSql()) {
			eco = new SQLEconomy(this);
		}
		else {
			eco = new YamlEconomy(this);
		}

		vaultImpl = new VaultImpl(this);
		
		balanceTopRunnable = new BalanceTopRunnable(this);
		balanceTopRunnable.start(configHandler.getBalanceTopInterval());
		
		if (!setupEconomy()) {
			disable("<red>Economy couldn't be registered, Vault plugin is missing!");
			return;
		}
		this.getLogger().info("Vault found, Economy has been registered.");

		if (configHandler.getLocale() == null) {
			disable("<red>Invalid locale configured! Change it in your config.yml");
			return;
		}

		moneyCommandHandler = new MoneyCommandHandler(this);
		this.getCommand("money").setExecutor(moneyCommandHandler);
		this.getCommand("money").setTabCompleter(moneyCommandHandler);
		
		BalanceCommand balanceCommand = new BalanceCommand(this);
		this.getCommand("balance").setExecutor(balanceCommand);
		this.getCommand("balance").setTabCompleter(balanceCommand);

		PayCommand payCommand = new PayCommand(this);
		this.getCommand("pay").setExecutor(payCommand);
		this.getCommand("pay").setTabCompleter(payCommand);

		BalanceTopCommand balanceTopCommand = new BalanceTopCommand(this);
		this.getCommand("balancetop").setExecutor(balanceTopCommand);
		this.getCommand("balancetop").setTabCompleter(balanceTopCommand);
		
		this.getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
		


	}

	@Override
	public void onDisable() {
		if (balanceTopRunnable != null) {
			try {
				balanceTopRunnable.cancel();
			} catch (IllegalStateException ignored) {
			}
		}
		if (eco != null) {
			eco.close();
		}
	}
	
	public static EconomyMain getInstance() {
		return instance;
	}

	private void addSqlColumns() {
		sqlColumns.put("Balance", "DECIMAL(65, 2) NOT NULL DEFAULT " + configHandler.getStartingBalance());
	}

	public ChatManager getChatManager() {
		return chatManager;
	}

	public ConfigHandler getConfigHandler() {
		return configHandler;
	}
	
	public String getPath() {
		return getDataFolder().getAbsolutePath();
	}
	
	public void warn(String message) {
		chatManager.sendMessage(Bukkit.getConsoleSender(), "<yellow>[WARNING] " + message);
	}
	
	public void disable(String message) {
		warn(message);
		Bukkit.getPluginManager().disablePlugin(this);
	}

	private boolean setupEconomy() {
		if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		this.getServer().getServicesManager().register(net.milkbowl.vault.economy.Economy.class, vaultImpl, this,
				ServicePriority.Highest);
		return true;
	}
	
	public boolean hasPermission(CommandSender sender, String node) {
		String shortNode = node;
		if (shortNode.startsWith("economy.")) {
			shortNode = shortNode.substring("economy.".length());
		} else if (shortNode.startsWith("essentialeconomy.")) {
			shortNode = shortNode.substring("essentialeconomy.".length());
		}
		String ecoPerm = "economy." + shortNode;
		String essentialPerm = "essentialeconomy." + shortNode;

		boolean ecoSet = sender.isPermissionSet(ecoPerm);
		boolean essentialSet = sender.isPermissionSet(essentialPerm);

		if (ecoSet && essentialSet) {
			return sender.hasPermission(ecoPerm) || sender.hasPermission(essentialPerm);
		}
		if (ecoSet) {
			return sender.hasPermission(ecoPerm);
		}
		if (essentialSet) {
			return sender.hasPermission(essentialPerm);
		}
		return sender.hasPermission(ecoPerm) || sender.hasPermission(essentialPerm);
	}

	public double getAmountFromString(String string) {
		if (string == null || string.trim().isEmpty()) {
			throw new NumberFormatException("Empty amount");
		}
		String cleaned = string.trim();
		int mult = 0;

		List<Map.Entry<String, Integer>> suffixes = new ArrayList<>(configHandler.getSuffixes().entrySet());
		suffixes.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getKey().length()).reversed());

		for (Map.Entry<String, Integer> suffix : suffixes) {
			String key = suffix.getKey();
			if (!key.isEmpty() && cleaned.endsWith(key)) {
				cleaned = cleaned.substring(0, cleaned.length() - key.length());
				mult = suffix.getValue();
				break;
			}
		}

		BigDecimal value = new BigDecimal(cleaned).movePointRight(mult).setScale(2, RoundingMode.HALF_UP);
		double result = value.doubleValue();
		if (!Double.isFinite(result)) {
			throw new NumberFormatException("Non-finite amount");
		}
		return result;
	}

	public OfflinePlayer getOfflinePlayerCached(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		Player online = Bukkit.getPlayerExact(name);
		if (online != null) {
			return online;
		}
		return Bukkit.getOfflinePlayerIfCached(name);
	}

	public ArrayList<OfflinePlayer> getPlayersFromString(CommandSender sender, String name) {
		ArrayList<OfflinePlayer> players = new ArrayList<>();
		if (name == null || name.isEmpty()) {
			return players;
		}

		if (name.equalsIgnoreCase("@a")) {
			players.addAll(Bukkit.getOnlinePlayers());
			return players;
		}

		OfflinePlayer player = getOfflinePlayerCached(name);
		if (player != null && (player.hasPlayedBefore() || player.isOnline())) {
			return new ArrayList<>(Collections.singletonList(player));
		}

		return players;
	}
	
	public static String format(double amount) {
		Locale locale = getInstance().configHandler.getLocale();
		NumberFormat numberFormat = NumberFormat.getCurrencyInstance(locale);
		String formatted = numberFormat.format(amount).replace("&nbsp", " ").replace("\u00A0", " ");
		if (getInstance().configHandler.isCustomSymbolEnabled()) {
			formatted = formatted.replace(Currency.getInstance(locale).getSymbol(locale), getInstance().configHandler.getCustomSymbol());
		}
		return formatted;
	}

	public Map<String, String> getSQLColumns() {
		return sqlColumns;
	}

	public BalanceTopRunnable getBalanceTopRunnable() {
		return balanceTopRunnable;
	}

	public void setBalanceTopRunnable(BalanceTopRunnable balanceTopRunnable) {
		this.balanceTopRunnable = balanceTopRunnable;
	}

	public MoneyCommandHandler getMoneyCommandHandler() {
		return moneyCommandHandler;
	}

	public Economy getEco() {
		return eco;
	}

}
