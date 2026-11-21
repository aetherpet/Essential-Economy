package lol.oddballs.EssentialEconomy.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import lol.oddballs.EssentialEconomy.EconomyMain;

public class PlayerJoinListener implements Listener {

	private final EconomyMain plugin;

	public PlayerJoinListener(EconomyMain plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		java.util.UUID uuid = player.getUniqueId();

		new BukkitRunnable() {
			@Override
			public void run() {
				if (!plugin.getEco().hasAccount(uuid)) {
					plugin.getEco().createAccount(uuid);
				}
			}
		}.runTaskAsynchronously(plugin);
	}
	
}
