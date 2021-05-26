package io.github.levtey.CobbleMelters;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import redempt.redlib.blockdata.DataBlock;
import redempt.redlib.misc.LocationUtils;

public class MelterCommand implements CommandExecutor {
	
	private CobbleMelters plugin;
	private final String noPerms = "&cYou don't have permission!";
	private final String reloaded = "{#green}Configs reloaded!";
	private final String given = "&e%player% {#green}has been given a &7&l%cobble%&0|{#orange}&l%lava% &eMelter.";
	
	public MelterCommand(CobbleMelters plugin) {
		this.plugin = plugin;
		plugin.getCommand("melters").setExecutor(this);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 0) return true;
		if (args[0].equalsIgnoreCase("reload")) {
			if (!sender.hasPermission("melters.reload")) return response(sender, noPerms);
			plugin.saveDefaultConfig();
			plugin.reloadConfig();
			return response(sender, reloaded);
		} else if (args[0].equalsIgnoreCase("give")) {
			if (!sender.hasPermission("melters.give")) return response(sender, noPerms);
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
			return response(sender, given
					.replace("%player%", targetPlayer.getName())
					.replace("%cobble%", cobble + "")
					.replace("%lava%", lava + ""));
		} else if (args[0].equalsIgnoreCase("debug")) {
			if (!sender.hasPermission("melters.debug")) return response(sender, noPerms);
			if (!(sender instanceof Player)) return true;
			Player player = (Player) sender;
			Block target = player.getTargetBlockExact(4);
			if (target == null) {
				player.sendMessage("All: ");
				plugin.getManager().getAll().forEach(db -> player.sendMessage(LocationUtils.toString(db.getBlock(), ":")));
				player.sendMessage("Loaded: ");
				plugin.getManager().getAllLoaded().forEach(db -> player.sendMessage(LocationUtils.toString(db.getBlock(), ":")));
			} else {
				DataBlock db = plugin.getManager().getExisting(target);
				player.sendMessage("This is " + (db == null ? "not  " : "") + "a data block");
			}
		}
		return true;
	}
	
	private boolean response(CommandSender sender, String message) {
		sender.sendMessage(plugin.makeReadable(message));
		return true;
	}

}
