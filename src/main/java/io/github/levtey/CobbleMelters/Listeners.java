package io.github.levtey.CobbleMelters;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import redempt.redlib.blockdata.DataBlock;
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
	}
	
	@EventHandler (ignoreCancelled = true, priority = EventPriority.LOW)
	public void onMelterBreak(BlockBreakEvent evt) {
		if (evt.getBlock().getType() != plugin.melterMaterial()) return;
		DataBlock dataBlock = plugin.getManager().getExisting(evt.getBlock());
		if (dataBlock == null) return;
		evt.setDropItems(false);
		Location blockLoc = evt.getBlock().getLocation();
		int lava = dataBlock.getInt(plugin.lavaData);
		int cobble = dataBlock.getInt(plugin.cobbleData);
		blockLoc.getWorld().dropItemNaturally(blockLoc, plugin.getMelter(lava, cobble));
	}
	
	@EventHandler
	public void on(PlayerInteractEvent evt) {
		if (evt.getAction() != Action.RIGHT_CLICK_BLOCK || evt.getHand() == EquipmentSlot.OFF_HAND) return;
		DataBlock dataBlock = plugin.getManager().getExisting(evt.getClickedBlock());
		if (dataBlock == null) return;
		evt.getPlayer().sendMessage(dataBlock.getInt(plugin.cobbleData) + " cobble | " + dataBlock.getInt(plugin.lavaData) + " lava");
	}
	
	@EventHandler
	public void on(IslandResetEvent evt) {
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
