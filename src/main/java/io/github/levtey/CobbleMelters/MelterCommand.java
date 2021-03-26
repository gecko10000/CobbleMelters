package io.github.levtey.CobbleMelters;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MelterCommand implements CommandExecutor {
	
	private CobbleMelters plugin;
	
	public MelterCommand(CobbleMelters plugin) {
		this.plugin = plugin;
		plugin.getCommand("melters").setExecutor(this);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) return true;
		if (args[0].equalsIgnoreCase("reload")) {
			if (!sender.hasPermission("melters.reload")) return true;
			plugin.saveDefaultConfig();
			plugin.reloadConfig();
		} else if (args[0].equalsIgnoreCase("give")) {
			Player targetPlayer = null;
			if (args.length > 1) {
				targetPlayer = Bukkit.getPlayer(args[1]);
			}
			if (targetPlayer == null && sender instanceof Player) targetPlayer = (Player) sender;
			if (targetPlayer == null) return true;
			int cobble = 0;
			int lava = 0;
			for (String arg : args) {
				if (arg.startsWith("-c:") && arg.length() > 3) {
					try {
						cobble = Integer.parseInt(arg.substring(3));
					} catch (NumberFormatException e) {}
				} else if (arg.startsWith("-l:") && arg.length() > 3) {
					try {
						lava = Integer.parseInt(arg.substring(3));
					} catch (NumberFormatException e) {}
				}
			}
			targetPlayer.getInventory().addItem(plugin.getMelter(lava, cobble));
		}
		return true;
	}

}
