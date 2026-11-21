package lol.oddballs.EssentialEconomy.runnables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.bukkit.scheduler.BukkitRunnable;

import lol.oddballs.EssentialEconomy.EconomyMain;
import lol.oddballs.EssentialEconomy.eco.PlayerBalance;

public class BalanceTopRunnable extends BukkitRunnable {
	
	private final EconomyMain plugin;
	private volatile List<PlayerBalance> balanceTop = Collections.emptyList();
	
	public BalanceTopRunnable(EconomyMain plugin) {
		this.plugin = plugin;
	}
	
	@Override
	public void run() {
		
		List<PlayerBalance> players = plugin.getEco().getPlayers();
		if (players == null) return;
		List<PlayerBalance> btop = new ArrayList<PlayerBalance>(players);
		btop.sort(Comparator.comparingDouble(PlayerBalance::getBalance).reversed());

		this.balanceTop = Collections.unmodifiableList(btop);
		
	}

	public void start(int interval) {
		
		this.runTaskTimerAsynchronously(plugin, 1, interval);
		
	}

	public List<PlayerBalance> getBalanceTop() {
		return balanceTop;
	}

}
