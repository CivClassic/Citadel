package com.untamedears.citadel.listener;

import static com.untamedears.citadel.Utility.createReinforcement;
import static com.untamedears.citadel.Utility.sendMessage;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.material.Openable;

import com.untamedears.citadel.Citadel;
import com.untamedears.citadel.GroupManager;
import com.untamedears.citadel.MemberManager;
import com.untamedears.citadel.PersonalGroupManager;
import com.untamedears.citadel.PlacementMode;
import com.untamedears.citadel.access.AccessDelegate;
import com.untamedears.citadel.entity.Faction;
import com.untamedears.citadel.entity.Member;
import com.untamedears.citadel.entity.PlayerState;
import com.untamedears.citadel.entity.Reinforcement;

/**
 * Created by IntelliJ IDEA.
 * User: chrisrico
 * Date: 3/21/12
 * Time: 9:57 PM
 * 
 * Last edited by JonnyD
 * 7/18/12
 */
public class PlayerListener implements Listener {
	
    @EventHandler
    public void login(PlayerLoginEvent ple) {
    	MemberManager memberManager = Citadel.getMemberManager();
    	memberManager.addOnlinePlayer(ple.getPlayer());

    	String playerName = ple.getPlayer().getDisplayName();
    	Member member = memberManager.getMember(playerName);
    	if(member == null){
    		member = new Member(playerName);
    		memberManager.addMember(member);
    	}
    	
		PersonalGroupManager personalGroupManager = Citadel.getPersonalGroupManager();
    	if(!personalGroupManager.hasPersonalGroup(playerName)){
    		GroupManager groupManager = Citadel.getGroupManager();
			String groupName = playerName;
			int i = 1;
    		while(groupManager.isGroup(groupName)){
    			groupName = playerName + i;
    			i++;
    		}
        	Faction group = new Faction(groupName, playerName);
    		groupManager.addGroup(group);
    		personalGroupManager.addPersonalGroup(groupName, playerName);
    	}
    }

    @EventHandler
    public void quit(PlayerQuitEvent pqe) {
    	Player player = pqe.getPlayer();
    	MemberManager memberManager = Citadel.getMemberManager();
    	memberManager.removeOnlinePlayer(player);
        PlayerState.remove(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void bookshelf(PlayerInteractEvent pie) {
        if (pie.hasBlock() && pie.getMaterial() == Material.BOOKSHELF)
            interact(pie);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent pie) {
        if (!pie.hasBlock()) return;

        Player player = pie.getPlayer();
        Block block = pie.getClickedBlock();

        AccessDelegate accessDelegate = AccessDelegate.getDelegate(block);
        block = accessDelegate.getBlock();
        Reinforcement reinforcement = accessDelegate.getReinforcement();

        checkAccessiblity(pie, reinforcement);
        if (pie.isCancelled()) return;

        PlayerState state = PlayerState.get(player);
        switch (state.getMode()) {
            case NORMAL:
            case FORTIFICATION:
                return;
            case INFO:
                // did player click on a reinforced block?
                if (reinforcement != null) {
                    ChatColor color = reinforcement.isAccessible(player) ? ChatColor.GREEN : ChatColor.RED;
                    sendMessage(player, color, "%s, security: %s", reinforcement.getStatus(), reinforcement.getSecurityLevel().name());
                }
                break;
            default:
                // player is in reinforcement mode
                if (reinforcement == null) {
                    createReinforcement(player, block);
                } else if (reinforcement.isBypassable(player)) {
                	boolean update = false;
                    if (reinforcement.getSecurityLevel() != state.getSecurityLevel()){
                        reinforcement.setSecurityLevel(state.getSecurityLevel());
                        update = true;
                    }
                   	if(!reinforcement.getOwner().equals(state.getFaction())) {
                        reinforcement.setOwner(state.getFaction());
                        update = true;
                    }
                   	if(update){
                        Citadel.getReinforcementManager().addReinforcement(reinforcement);
                        sendMessage(player, ChatColor.GREEN, "Changed security level %s", reinforcement.getSecurityLevel().name());
                    }
                } else {
                    sendMessage(player, ChatColor.RED, "You are not permitted to modify this reinforcement");
                }
                pie.setCancelled(true);
                if (state.getMode() == PlacementMode.REINFORCEMENT_SINGLE_BLOCK) {
                    state.reset();
                } else {
                    state.checkResetMode();
                }
        }
    }

    private void checkAccessiblity(PlayerInteractEvent pie, Reinforcement reinforcement) {
        if (reinforcement != null
                && reinforcement.isSecurable()
                && !reinforcement.isAccessible(pie.getPlayer())) {
            if (pie.getAction() == Action.LEFT_CLICK_BLOCK && reinforcement.getBlock().getState().getData() instanceof Openable) {
                // because openable objects can be opened with a left or right click
                // and we want to deny use but allow destruction, we need to limit
                // opening secured doors to right clicking
                pie.setUseInteractedBlock(Event.Result.DENY);
            } else if (pie.getAction() == Action.RIGHT_CLICK_BLOCK) {
            	Citadel.info("%s failed to access locked reinforcement %s, " 
            			+ pie.getPlayer().getDisplayName() + " at " 
            			+ reinforcement.getBlock().getLocation().toString());
                // this block is secured and is inaccesible to the player
                sendMessage(pie.getPlayer(), ChatColor.RED, "%s is locked", pie.getClickedBlock().getType().name());
                pie.setCancelled(true);
            }
        }
    }
}
