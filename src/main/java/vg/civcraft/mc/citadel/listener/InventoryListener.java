package vg.civcraft.mc.citadel.listener;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import vg.civcraft.mc.citadel.CitadelPermissionHandler;
import vg.civcraft.mc.citadel.ReinforcementLogic;
import vg.civcraft.mc.citadel.model.Reinforcement;
import vg.civcraft.mc.civmodcore.api.WorldAPI;

import java.util.Objects;

public class InventoryListener implements Listener {

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onInventoryMoveItemEvent(InventoryMoveItemEvent event) {
		Inventory fromInventory = event.getSource();
		InventoryHolder fromHolder = fromInventory.getHolder();
		boolean isFromBlock = fromHolder instanceof Container;
		boolean fromAtChunkBorder = false;
		Location fromLocation = null;
		if (isFromBlock) {
			fromLocation = fromInventory.getLocation();
			fromAtChunkBorder = isAtChunkBorder(fromLocation);
			if (fromAtChunkBorder && !WorldAPI.isBlockLoaded(fromLocation)) {
				event.setCancelled(true);
				return;
			}
		}

		Inventory destInventory = event.getDestination();
		InventoryHolder destHolder = destInventory.getHolder();
		boolean isDestBlock = destHolder instanceof Container;
		boolean destAtChunkBorder = false;
		Location destLocation = null;
		if (isDestBlock) {
			destLocation = destInventory.getLocation();
			destAtChunkBorder = isAtChunkBorder(destLocation);
			if (destAtChunkBorder && !WorldAPI.isBlockLoaded(destLocation)) {
				event.setCancelled(true);
				return;
			}
		} else {
			if (!isFromBlock) {
				// neither is a block, just ignore entirely
				return;
			}
		}

		// Determine the reinforcement of the source
		Reinforcement fromReinforcement = null;
		if (isFromBlock) {
			if (fromAtChunkBorder && fromHolder instanceof DoubleChest) {
				DoubleChest doubleChest = (DoubleChest) fromHolder;
				Location chestLocation = ((Chest) doubleChest.getLeftSide()).getLocation();
				Location otherLocation = ((Chest) doubleChest.getRightSide()).getLocation();
				// [LagFix] If either side of the double chest is not loaded then the
				// reinforcement cannot be retrieved
				// [LagFix] without necessarily loading the chunk to check against reinforcement
				// logic, therefore this
				// [LagFix] should err on the side of caution and prevent the transfer.
				if (!WorldAPI.isBlockLoaded(chestLocation) || !WorldAPI.isBlockLoaded(otherLocation)) {
					event.setCancelled(true);
					return;
				}
			}
			fromReinforcement = ReinforcementLogic.getReinforcementProtecting(fromLocation.getBlock());
		}
		// Determine the reinforcement of the destination
		Reinforcement destReinforcement = null;
		if (isDestBlock) {
			if (destAtChunkBorder && destHolder instanceof DoubleChest) {
				DoubleChest doubleChest = (DoubleChest) destHolder;
				Location chestLocation = ((Chest) doubleChest.getLeftSide()).getLocation();
				Location otherLocation = ((Chest) doubleChest.getRightSide()).getLocation();
				// [LagFix] If either side of the double chest is not loaded then the
				// reinforcement cannot be retrieved
				// [LagFix] without necessarily loading the chunk to check against reinforcement
				// logic, therefore this
				// [LagFix] should err on the side of caution and prevent the transfer.
				if (!WorldAPI.isBlockLoaded(chestLocation) || !WorldAPI.isBlockLoaded(otherLocation)) {
					event.setCancelled(true);
					return;
				}
			}
			destReinforcement = ReinforcementLogic.getReinforcementProtecting(destLocation.getBlock());
		}
		// Allow the transfer if neither are reinforced
		if (fromReinforcement == null && destReinforcement == null) {
			return;
		}
		// Allow the transfer if the destination is un-reinforced and the source is
		// insecure
		if (destReinforcement == null) {
			if (!fromReinforcement.isInsecure()) {
				event.setCancelled(true);
			}
			return;
		}
		// Allow the transfer if the source is un-reinforced and the destination is
		// insecure
		if (fromReinforcement == null) {
			if (!destReinforcement.isInsecure()) {
				event.setCancelled(true);
			}
			return;
		}
		// Allow the transfer if both the source and destination are insecure
		if (fromReinforcement.isInsecure() && destReinforcement.isInsecure()) {
			return;
		}
		// Allow the transfer if both the source and destination are on the same group
		if (fromReinforcement.getGroupId() == destReinforcement.getGroupId()) {
			return;
		}
		event.setCancelled(true);
	}
	
	private static boolean isAtChunkBorder(Location location) {
		int xShif = location.getBlockX() & 15;
		int zShif = location.getBlockZ() & 15;
		return xShif == 0 || xShif == 15 || zShif == 0 || zShif == 15;
	}

	@EventHandler
	public void onInventoryOpen(InventoryOpenEvent event) {
		if (isInventoryWithdrawalBlocked(event)) {
			event.getPlayer().sendMessage(ChatColor.RED + "This container is locked. You lack the permission to move items ("
					+ CitadelPermissionHandler.getChestWithdraw().getName() + ")");
		}
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (isInventoryWithdrawalBlocked(event)) {
			event.setCancelled(true);
		}
	}

	public boolean isInventoryWithdrawalBlocked(InventoryEvent event) {
		Inventory inv = event.getInventory();
		InventoryHolder holder = inv.getHolder();
		Block block;
		Player player;
		if (holder instanceof Player) {
			holder = event.getView().getTopInventory().getHolder();
		}
		if (holder instanceof Container) {
			Container container = (Container) holder;
			block = container.getBlock();
		} else if (holder instanceof DoubleChest) {
			DoubleChest doubleChest = (DoubleChest) holder;
			block =  Objects.requireNonNull((Chest) doubleChest.getLeftSide()).getBlock();
		} else {
			return false;
		}
		Reinforcement rein = ReinforcementLogic.getReinforcementProtecting(block);
		if (rein == null) {
			return false;
		}
		if (event instanceof InventoryOpenEvent) {
			player = (Player) ((InventoryOpenEvent) event).getPlayer();
		} else if (event instanceof InventoryClickEvent) {
			player = (Player) ((InventoryClickEvent) event).getWhoClicked();
		} else {
			return false;
		}
		return !rein.hasPermission(player, CitadelPermissionHandler.getChestWithdraw());
	}

}
