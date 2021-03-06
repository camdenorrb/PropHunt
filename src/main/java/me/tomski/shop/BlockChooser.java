package me.tomski.shop;


import me.tomski.language.MessageBank;
import me.tomski.objects.SimpleDisguise;
import me.tomski.prophunt.DisguiseManager;
import me.tomski.prophunt.GameManager;
import me.tomski.prophunt.PropHunt;
import me.tomski.utils.PropHuntMessaging;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BlockChooser implements Listener {

    private PropHunt plugin;
    private List<Player> inChooser = new ArrayList<Player>();

    public BlockChooser(PropHunt plugin) {
        this.plugin = plugin;
    }

    public void openBlockShop(Player p) {
        if (!GameManager.playersWaiting.contains(p.getName())) {
            PropHuntMessaging.sendMessage(p, MessageBank.NOT_IN_LOBBY.getMsg());
            return;
        }
        Inventory inv = Bukkit.createInventory(p, getShopSize(plugin.getShopSettings().blockChoices.size()), MessageBank.DISGUISE_NAME.getMsg());
        for (ShopItem sI : plugin.getShopSettings().blockChoices) {
            sI.addToInventory(inv, p);
        }
        p.openInventory(inv);
        inChooser.add(p);
    }

    @EventHandler
    public void onInventClick(InventoryClickEvent e) {
        if (inChooser.contains((Player) e.getWhoClicked())) {
            if (e.getCurrentItem() != null) {
                if (!hasPermsForBlock((Player) e.getWhoClicked(), e.getCurrentItem())) {
                    PropHuntMessaging.sendMessage((Player) e.getWhoClicked(), MessageBank.NO_BLOCK_CHOICE_PERMISSION.getMsg());
                    e.setCancelled(true);
                    return;
                }
                if (e.getCurrentItem().getType().equals(Material.AIR)) {
                    return;
                }
                if (GameManager.playersWaiting.contains(((Player) e.getWhoClicked()).getName())) {
                    DisguiseManager.preChosenDisguise.put((Player) e.getWhoClicked(), parseItemToDisguise(e.getCurrentItem()));
                    PropHuntMessaging.sendMessage((Player) e.getWhoClicked(), MessageBank.SHOP_CHOSEN_DISGUISE.getMsg() + e.getCurrentItem().getItemMeta().getDisplayName());
                    e.getView().close();
                } else {
                    PropHuntMessaging.sendMessage((Player) e.getWhoClicked(), MessageBank.BLOCK_ACCESS_IN_GAME.getMsg());
                }
            }
        }
    }

    private boolean hasPermsForBlock(Player player, ItemStack currentItem) {
        if (currentItem.getData().getData() == 0) {
            return player.hasPermission("prophunt.blockchooser." + currentItem.getTypeId());
        } else {
            return player.hasPermission("prophunt.blockchooser." + currentItem.getTypeId() + "-" + currentItem.getData().getData());
        }
    }

    private SimpleDisguise parseItemToDisguise(ItemStack itemStack) {
        return new SimpleDisguise(itemStack.getTypeId(), (int) itemStack.getData().getData(), null);
    }

    @EventHandler
    public void inventoryClose(InventoryCloseEvent e) {
        if (inChooser.contains(e.getPlayer())) {
            inChooser.remove(e.getPlayer());
        }
    }

    private int getShopSize(int n) {
        return (int) Math.ceil(n / 9.0) * 9;
    }
}
