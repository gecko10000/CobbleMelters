package io.github.levtey.CobbleMelters;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import redempt.redlib.blockdata.DataBlock;
import redempt.redlib.blockdata.events.DataBlockMoveEvent;
import redempt.redlib.itemutils.ItemUtils;
import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.events.island.IslandResetEvent;
import world.bentobox.bentobox.database.objects.Island;

public class Listeners implements Listener {

	private CobbleMelters plugin;
	
	public Listeners(CobbleMelters plugin) {
		this.plugin = plugin;
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}
	
	@EventHandler
	public void onMelterPlace(BlockPlaceEvent evt) {
		if (evt.getItemInHand().getType() != plugin.melterMaterial()) return;
		PersistentDataContainer itemPDC = evt.getItemInHand().getItemMeta().getPersistentDataContainer();
		Integer cobble = itemPDC.get(plugin.cobbleKey, PersistentDataType.INTEGER);
		if (cobble == null) return;
		Integer lava = itemPDC.getOrDefault(plugin.lavaKey, PersistentDataType.INTEGER, 0);
		DataBlock dataBlock = plugin.getManager().getDataBlock(evt.getBlock());
		dataBlock.set(plugin.cobbleData, cobble);
		dataBlock.set(plugin.lavaData, lava);
		plugin.doRedstone(dataBlock);
	}
	
	@EventHandler (priority = EventPriority.HIGH)
	public void onRedstonePlace(BlockPlaceEvent evt) {
		Block block = evt.getBlock();
		if (block.getType() != Material.REDSTONE_WIRE) return;
		for (BlockFace face : plugin.redstonePowerDirections) {
			DataBlock dataBlock = plugin.getManager().getExisting(block.getRelative(face));
			if (dataBlock == null) continue;
			plugin.doRedstone(dataBlock);
		}
	}
	
	@EventHandler (ignoreCancelled = true, priority = EventPriority.LOW)
	public void onMelterBreak(BlockBreakEvent evt) {
		if (evt.getPlayer().getGameMode() == GameMode.CREATIVE || evt.getBlock().getType() != plugin.melterMaterial()) return;
		DataBlock dataBlock = plugin.getManager().getExisting(evt.getBlock());
		if (dataBlock == null) return;
		evt.setDropItems(false);
		Location blockLoc = evt.getBlock().getLocation();
		int lava = dataBlock.getInt(plugin.lavaData);
		int cobble = dataBlock.getInt(plugin.cobbleData);
		blockLoc.getWorld().dropItemNaturally(blockLoc, plugin.getMelter(lava, cobble));
	}
	
	@EventHandler
	public void onRightClick(PlayerInteractEvent evt) {
		if (evt.getAction() != Action.RIGHT_CLICK_BLOCK || evt.getHand() == EquipmentSlot.OFF_HAND) return;
		DataBlock dataBlock = plugin.getManager().getExisting(evt.getClickedBlock());
		if (dataBlock == null) return;
		Player player = evt.getPlayer();
		PlayerInventory playerInv = player.getInventory();
		if (dataBlock.getInt(plugin.lavaData) > 0 && evt.getItem() != null && evt.getItem().getType() == Material.BUCKET) {
			Island island = BentoBox.getInstance().getIslands().getProtectedIslandAt(evt.getClickedBlock().getLocation()).orElse(null);
			if (!player.hasPermission("melters.bypass") && (island == null || !island.getMemberSet().contains(player.getUniqueId()))) return;
			dataBlock.set(plugin.lavaData, dataBlock.getInt(plugin.lavaData) - 1);
			ItemUtils.remove(playerInv, Material.BUCKET, 1);
			playerInv.addItem(new ItemStack(Material.LAVA_BUCKET));
		} else {
			evt.getPlayer().sendMessage(dataBlock.getInt(plugin.cobbleData) + " cobble | " + dataBlock.getInt(plugin.lavaData) + " lava");
		}
	}
	
	@EventHandler
	public void onDispense(BlockDispenseEvent evt) {
		Block block = evt.getBlock();
		if (block.getType() != Material.DISPENSER) return;
		if (evt.getItem().getType() != Material.BUCKET) return;
		org.bukkit.block.data.type.Dispenser blockData = (org.bukkit.block.data.type.Dispenser) block.getBlockData();
		DataBlock melter = plugin.getManager().getExisting(block.getRelative(blockData.getFacing()));
		if (melter == null) return;
		evt.setCancelled(true);
		if (melter.getInt(plugin.lavaData) <= 0) return;
		Dispenser dispenser = (Dispenser) block.getState();
		int buckets = ItemUtils.countAndRemove(dispenser.getInventory(), Material.BUCKET, 1);
		if (buckets == 0) return;
		melter.set(plugin.lavaData, melter.getInt(plugin.lavaData) - 1);
		plugin.doRedstone(melter);
		Block belowMelter = melter.getBlock().getRelative(BlockFace.DOWN);
		Location belowLocation = belowMelter.getLocation();
		if (belowMelter.getType() != Material.HOPPER) {
			belowLocation.getWorld().dropItem(belowLocation.add(0.5, 0.5, 0.5), new ItemStack(Material.LAVA_BUCKET));
		} else {
			Map<Integer, ItemStack> extra = ((Hopper) belowMelter.getState()).getInventory().addItem(new ItemStack(Material.LAVA_BUCKET));
			for (ItemStack extraItem : extra.values()) {
				belowLocation.getWorld().dropItem(belowLocation.add(0.5, 0.5, 0.5), extraItem);
			}
		}
	}
	
	@EventHandler
	public void onChunkLoad(ChunkLoadEvent evt) {
		Chunk chunk = evt.getChunk();
		Location chunkCenter = chunk.getBlock(8, 0, 8).getLocation();
		for (DataBlock dataBlock : plugin.getManager().getNearby(chunkCenter, 8)) {
			if (!dataBlock.getBlock().getChunk().equals(chunk)) continue;
			plugin.doRedstone(dataBlock);
		}
	}
	
	@EventHandler
	public void onRedstoneChange(BlockRedstoneEvent evt) {
		Block block = evt.getBlock();
		if (block.getType() != Material.REDSTONE_WIRE) return;
		int currentToSet = -1;
		for (BlockFace face : plugin.redstonePowerDirections) {
			DataBlock dataBlock = plugin.getManager().getExisting(block.getRelative(face));
			if (dataBlock == null) continue;
			int lava = dataBlock.getInt(plugin.lavaData);
			if (lava > currentToSet) currentToSet = Math.min(15, lava);
		}
		if (currentToSet == -1) return;
		evt.setNewCurrent(currentToSet);
	}
	
	@EventHandler
	public void onMelterMove(DataBlockMoveEvent evt) {
		Bukkit.getScheduler().runTask(plugin, () -> {
			plugin.doRedstone(plugin.getManager().getExisting(evt.getTo().getBlock()));
		});
	}
	
	@EventHandler
	public void onCreatureSpawn(CreatureSpawnEvent evt) {
		if (evt.getSpawnReason() != SpawnReason.BUILD_IRONGOLEM) return;
		Block center = evt.getLocation().getBlock();
		for (int x = center.getX() - 1; x <= center.getX() + 1; x++) {
			for (int y = center.getY() - 1; y <= center.getY() + 1; y++) {
				for (int z = center.getZ() - 1; z <= center.getZ() + 1; z++) {
					DataBlock dataBlock = plugin.getManager().getExisting(center.getWorld().getBlockAt(x, y, z));
					if (dataBlock != null) {
						evt.setCancelled(true);
						return;
					}
				}
			}
		}
	}
	
	@EventHandler
	public void onReset(IslandResetEvent evt) {
		int islandSize = BentoBox.getInstance()
				.getAddonsManager().getAddonByName("BSkyBlock").get()
				.getConfig().getInt("world.distance-between-islands");
		Island island = evt.getIsland();
		for (DataBlock dataBlock : plugin.getManager().getNearby(island.getCenter(), islandSize)) {
			if (!island.inIslandSpace(dataBlock.getBlock().getLocation())) continue;
			plugin.getManager().remove(dataBlock);
		}
		plugin.getManager().save();
	}
	
}
