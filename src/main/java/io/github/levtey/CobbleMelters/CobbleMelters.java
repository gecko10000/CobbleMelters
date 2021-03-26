package io.github.levtey.CobbleMelters;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.Zrips.CMI.Containers.CMIChatColor;

import net.md_5.bungee.api.ChatColor;
import redempt.redlib.blockdata.BlockDataManager;
import redempt.redlib.blockdata.DataBlock;
import redempt.redlib.itemutils.ItemUtils;

public class CobbleMelters extends JavaPlugin {
	
	public final String cobbleData = "cobble";
	public final String lavaData = "lava";
	private boolean isCMIEnabled = Bukkit.getPluginManager().isPluginEnabled("CMI");
	private BlockDataManager manager;
	public final NamespacedKey cobbleKey = new NamespacedKey(this, "cobble");
	public final NamespacedKey lavaKey = new NamespacedKey(this, "lava");
	private final Set<BlockFace> inputDirections = EnumSet.of(BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
	private BukkitTask hopperTask;
	private BukkitTask meltOrPullTask;

	public void onEnable() {
		saveDefaultConfig();
		manager = new BlockDataManager(getDataFolder().toPath().resolve("melters.db"));
		new Listeners(this);
		new MelterCommand(this);
		hopperTask = hopperTask();
		meltOrPullTask = meltOrPullTask();
	}
	
	public void onDisable() {
		hopperTask.cancel();
		manager.saveAndClose();
	}
	
	public BukkitTask hopperTask() {
		return Bukkit.getScheduler().runTaskTimer(this, () -> {
			for (DataBlock dataBlock : manager.getAllLoaded()) {
				doHopperInput(dataBlock);
			}
		}, 0L, Bukkit.spigot().getConfig().getLong("world-settings.default.ticks-per.hopper-transfer"));
	}
	
	public BukkitTask meltOrPullTask() {
		return Bukkit.getScheduler().runTaskTimer(this, () -> {
			for (DataBlock dataBlock : manager.getAllLoaded()) {
				doTickOrPull(dataBlock);
			}
		}, 0L, getConfig().getLong("meltInterval", 200));
	}
	
	public void doHopperInput(DataBlock dataBlock) {
		Block block = dataBlock.getBlock();
		for (BlockFace face : inputDirections) {
			Block adjacent = block.getRelative(face);
			if (adjacent.getType() != Material.HOPPER) continue;
			BlockFace facing = ((Directional) adjacent.getBlockData()).getFacing();
			if (face == BlockFace.UP && facing != BlockFace.DOWN) continue;
			if (face == BlockFace.NORTH && facing != BlockFace.SOUTH) continue;
			if (face == BlockFace.SOUTH && facing != BlockFace.NORTH) continue;
			if (face == BlockFace.EAST && facing != BlockFace.WEST) continue;
			if (face == BlockFace.WEST && facing != BlockFace.EAST) continue;
			Hopper hopper = (Hopper) adjacent.getState();
			int amountToFind = Bukkit.spigot().getConfig().getInt("world-settings.default.hopper-amount", 1);
			int movedAmount = Math.min(amountToFind, ItemUtils.countAndRemove(hopper.getInventory(), Material.COBBLESTONE, amountToFind));
			dataBlock.set(cobbleData, dataBlock.getInt(cobbleData) + movedAmount);
		}
	}
	
	public void doTickOrPull(DataBlock dataBlock) {
		Block underBlock = dataBlock.getBlock().getRelative(BlockFace.DOWN);
		Material underBlockType = underBlock.getType();
		if (underBlockType == Material.HOPPER && dataBlock.getInt(lavaData) > 0) {
			Inventory hopperInv = ((Hopper) underBlock.getState()).getInventory();
			int buckets = ItemUtils.countAndRemove(hopperInv, Material.BUCKET, 1);
			if (buckets == 0) return;
			dataBlock.set(lavaData, dataBlock.getInt(lavaData) - 1);
			hopperInv.addItem(new ItemStack(Material.LAVA_BUCKET));
		} else if (underBlockType == Material.LAVA || underBlockType == Material.FIRE) {
			int progress = dataBlock.getInt(cobbleData);
			int required = getConfig().getInt("cobblePerLava");
			if (progress < required) return;
			dataBlock.set(cobbleData, dataBlock.getInt(cobbleData) - required);
			dataBlock.set(lavaData, dataBlock.getInt(lavaData) + 1);
		}
	}
	
	public ItemStack getMelter(int lava, int cobble) {
		ItemStack melter = new ItemStack(melterMaterial());
		ItemMeta melterMeta = melter.getItemMeta();
		melterMeta.setDisplayName(makeReadable(getConfig().getString("item.name")));
		melterMeta.setLore(getConfig().getStringList("item.lore").stream()
				.map(str -> str.replace("%lava%", lava + ""))
				.map(str -> str.replace("%cobble%", cobble + ""))
				.map(this::makeReadable).collect(Collectors.toList()));
		if (getConfig().getBoolean("item.enchanted")) {
			melterMeta.addEnchant(Enchantment.DURABILITY, 1, true);
			melterMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		}
		melterMeta.getPersistentDataContainer().set(lavaKey, PersistentDataType.INTEGER, lava);
		melterMeta.getPersistentDataContainer().set(cobbleKey, PersistentDataType.INTEGER, cobble);
		melter.setItemMeta(melterMeta);
		return melter;
	}
	
	public Material melterMaterial() {
		Material melterMaterial = Material.getMaterial(getConfig().getString("item.material").toUpperCase());
		if (melterMaterial == null) melterMaterial = Material.IRON_BLOCK;
		return melterMaterial;
	}
	
	public BlockDataManager getManager() {
		return manager;
	}
	
	public String makeReadable(String input) {
		return isCMIEnabled ? CMIChatColor.colorize(input) : ChatColor.translateAlternateColorCodes('&', input);
	}
	
}
