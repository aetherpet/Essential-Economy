package lol.oddballs.EssentialEconomy.eco;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lol.oddballs.EssentialEconomy.EconomyMain;
import lol.oddballs.EssentialEconomy.data.YamlData;

public class YamlEconomy implements Economy {
	
	private final EconomyMain plugin;
	private final Object lock = new Object();
	
	public YamlEconomy(EconomyMain plugin) {
		this.plugin = plugin;
		
		Path dataDir = Paths.get(plugin.getPath(), "data");
		if (!Files.exists(dataDir)) {
			try {
				Files.createDirectories(dataDir);
			} catch (IOException e) {
				plugin.warn("There was an error creating the data directory.");
	            return;
			}
		}
		
	}

	@Override
	public boolean createAccount(UUID uuid) {
		synchronized (lock) {
			if (hasAccount(uuid)) {
				return true;
			}
			return set(uuid, plugin.getConfigHandler().getStartingBalance());
		}
	}

	@Override
	public boolean hasAccount(UUID uuid) {
		synchronized (lock) {
			File file = new File(plugin.getPath() + "/data/" + uuid.toString() + ".yml");
			return file.exists();
		}
	}

	@Override
	public boolean delete(UUID uuid) {
		synchronized (lock) {
			File islandFile = new File(plugin.getPath() + "/data/" + uuid.toString() + ".yml");
			return islandFile.delete();
		}
	}

	@Override
	public boolean withdraw(UUID uuid, double amount) {
		if (!Double.isFinite(amount) || amount < 0)
			return false;
		synchronized (lock) {
			double current = getBalance(uuid).getBalance();
			if (current < amount)
				return false;
			return set(uuid, current - amount);
		}
	}

	@Override
	public boolean deposit(UUID uuid, double amount) {
		if (!Double.isFinite(amount) || amount < 0)
			return false;
		synchronized (lock) {
			return set(uuid, getBalance(uuid).getBalance() + amount);
		}
	}

	@Override
	public boolean set(UUID uuid, double amount) {
		if (!Double.isFinite(amount) || amount < 0)
			return false;
		synchronized (lock) {
			YamlData data = new YamlData(uuid.toString() + ".yml", plugin.getPath() + "/data");
			data.getConfig().set("UUID", uuid.toString());
			data.getConfig().set("Balance", amount);
			data.saveConfig();
			return true;
		}
	}

	@Override
	public boolean has(UUID uuid, double amount) {
		if (!Double.isFinite(amount))
			return false;
		synchronized (lock) {
			return getBalance(uuid).getBalance() >= amount;
		}
	}

	@Override
	public PlayerBalance getBalance(UUID uuid) {
		synchronized (lock) {
			try {
				if (!hasAccount(uuid)) {
					return new PlayerBalance(uuid, 0);
				}
				YamlData data = new YamlData(uuid.toString() + ".yml", plugin.getPath() + "/data");
				double balance = data.getConfig().getDouble("Balance");
				return new PlayerBalance(uuid, balance);
			} catch (Exception e) {
				return new PlayerBalance(uuid, 0);
			}
		}
	}

	@Override
	public List<PlayerBalance> getPlayers() {
		synchronized (lock) {
			List<PlayerBalance> playerData = new ArrayList<PlayerBalance>();
			File[] files = new File(plugin.getPath() + "/data").listFiles();
			if (files == null) return playerData;
			for (File file : files) {
				if (!file.isFile() || !file.getName().endsWith(".yml")) {
					continue;
				}
				String rawUuid = file.getName().substring(0, file.getName().length() - 4);
				try {
					UUID uuid = UUID.fromString(rawUuid);
					playerData.add(getBalance(uuid));
				} catch (IllegalArgumentException ignored) {
				}
			}
			return playerData;
		}
	}

}
