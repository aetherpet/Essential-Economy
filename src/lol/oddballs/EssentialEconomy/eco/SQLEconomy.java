package lol.oddballs.EssentialEconomy.eco;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;

import lol.oddballs.EssentialEconomy.EconomyMain;
import lol.oddballs.EssentialEconomy.data.ConfigHandler;
import lol.oddballs.EssentialEconomy.data.MySQL;

public class SQLEconomy implements Economy {
	
	private final EconomyMain plugin;
	private final MySQL sql;
	
	public SQLEconomy(EconomyMain plugin) {
		this.plugin = plugin;
		ConfigHandler config = plugin.getConfigHandler();
		
		this.sql = new MySQL(
				config.getMysqlHost(), 
				config.getMysqlPort(), 
				config.getMysqlDatabase(), 
				config.getMysqlUsername(), 
				config.getMysqlPassword());
		
		connectToSQL();
		
		if (sql.isConnected()) {
			
			try (Statement statement = sql.getConnection().createStatement()) {
				DatabaseMetaData md = sql.getConnection().getMetaData();
				statement.execute("CREATE TABLE IF NOT EXISTS Economy (UUID VARCHAR(36) NOT NULL PRIMARY KEY);");
				for (Map.Entry<String, String> column : plugin.getSQLColumns().entrySet()) {
					try (ResultSet rs = md.getColumns(null, null, "Economy", column.getKey())) {
						if (!rs.next()) {
							statement.execute("ALTER TABLE Economy ADD " + column.getKey() + " " + column.getValue() + ";");
						}
					}
				}
			} catch (SQLException e) {
				plugin.disable("There was an error with creating the database table.");
				return;
			}
			
			try (PreparedStatement statement = sql.getConnection().prepareStatement("ALTER TABLE Economy "
					+ "MODIFY COLUMN Balance " + plugin.getSQLColumns().get("Balance"))) {
				statement.executeUpdate();
			} catch (SQLException e) {
				plugin.disable("There was an error updating the sql balance from 1dp to 2dp.");
				return;
			}
		
		}
	
	}
	
	private void connectToSQL() {
		try {
			sql.connect();
            plugin.warn("Successfully connected to mysql database.");
        } 
        catch (SQLException e) {
        	plugin.warn("There was an error connecting to the database. " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(plugin);
            return;
        }
        catch (ClassNotFoundException e) {
        	plugin.getLogger().warning("The MySQL driver class could not be found.");
        	Bukkit.getPluginManager().disablePlugin(plugin);
        	return;
        }
	}

	@Override
	public boolean createAccount(UUID uuid) {
		PlayerBalance playerBalance = new PlayerBalance(uuid, plugin.getConfigHandler().getStartingBalance());
		try (PreparedStatement statement = sql.getConnection().prepareStatement("INSERT IGNORE INTO Economy "
				+ "(UUID, Balance) VALUES (?, ?);")) {
			statement.setString(1, playerBalance.getUUID().toString());
			statement.setDouble(2, playerBalance.getBalance());
			statement.executeUpdate();
		} catch (SQLException e) {
			plugin.warn(e.getMessage());
			return false;
		}
		return true;
	}

	@Override
	public boolean hasAccount(UUID uuid) {
		try (PreparedStatement statement = sql.getConnection().prepareStatement("SELECT UUID FROM Economy WHERE UUID=?")) {
			statement.setString(1, uuid.toString());
			try (ResultSet result = statement.executeQuery()) {
				return result.next();
			}
		} catch (SQLException e) {
			plugin.warn(e.getMessage());
			return false;
		}
	}

	@Override
	public boolean delete(UUID uuid) {
		try (PreparedStatement statement = sql.getConnection().prepareStatement("DELETE FROM Economy "
				+ "WHERE UUID=?")) {
			statement.setString(1, uuid.toString());
			statement.executeUpdate();
		} catch(SQLException e) {
			plugin.warn(e.getMessage());
			return false;
		}
		return true;
	}

	@Override
	public boolean withdraw(UUID uuid, double amount) {
		if (!Double.isFinite(amount) || amount < 0)
			return false;
		try (PreparedStatement statement = sql.getConnection().prepareStatement(
				"UPDATE Economy SET Balance = Balance - ? WHERE UUID = ? AND Balance >= ?")) {
			statement.setDouble(1, amount);
			statement.setString(2, uuid.toString());
			statement.setDouble(3, amount);
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			plugin.warn(e.getMessage());
			return false;
		}
	}

	@Override
	public boolean deposit(UUID uuid, double amount) {
		if (!Double.isFinite(amount) || amount < 0)
			return false;
		try (PreparedStatement statement = sql.getConnection().prepareStatement(
				"UPDATE Economy SET Balance = Balance + ? WHERE UUID = ?")) {
			statement.setDouble(1, amount);
			statement.setString(2, uuid.toString());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			plugin.warn(e.getMessage());
			return false;
		}
	}

	@Override
	public boolean set(UUID uuid, double amount) {
		if (!Double.isFinite(amount) || amount < 0)
			return false;
		try (PreparedStatement statement = sql.getConnection().prepareStatement("UPDATE Economy SET "
				+ "Balance=? WHERE UUID=?")) {
			statement.setDouble(1, amount);
			statement.setString(2, uuid.toString());
			statement.executeUpdate();
		} catch(SQLException e) {
			plugin.warn(e.getMessage());
			return false;
		}
		return true;
	}

	@Override
	public boolean has(UUID uuid, double amount) {
		return Double.isFinite(amount) && getBalance(uuid).getBalance() >= amount;
	}

	@Override
	public PlayerBalance getBalance(UUID uuid) {
		try (PreparedStatement statement = sql.getConnection().prepareStatement("SELECT Balance FROM Economy "
				+ "WHERE UUID=?")) {
			statement.setString(1, uuid.toString());
			try (ResultSet result = statement.executeQuery()) {
				if (result.next()) {
					return new PlayerBalance(uuid, result.getDouble("Balance"));
				}
				return new PlayerBalance(uuid, 0);
			}
		} catch (SQLException e) {
			return new PlayerBalance(uuid, 0);
		}
	}
	
	@Override
	public List<PlayerBalance> getPlayers() {
		try (Statement statement = sql.getConnection().createStatement();
			 ResultSet result = statement.executeQuery("SELECT UUID, Balance FROM Economy;")) {
			List<PlayerBalance> playerData = new ArrayList<PlayerBalance>();
			while (result.next()) {
				try {
					UUID uuid = UUID.fromString(result.getString("UUID"));
					double balance = result.getDouble("Balance");
					playerData.add(new PlayerBalance(uuid, balance));
				} catch (IllegalArgumentException ignored) {
				}
			}
			return playerData;
		} catch (SQLException e) {
			plugin.warn(e.getMessage());
			return Collections.emptyList();
		}
	}

	@Override
	public void close() {
		try {
			sql.disconnect();
		} catch (SQLException e) {
			plugin.warn("Error closing MySQL connection: " + e.getMessage());
		}
	}

}
