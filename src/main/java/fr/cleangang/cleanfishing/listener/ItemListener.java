package fr.cleangang.cleanfishing.listener;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.game.Game;
import fr.cleangang.cleanfishing.gui.AdminGui;
import fr.cleangang.cleanfishing.gui.ArenaMenuGui;
import fr.cleangang.cleanfishing.gui.CodexGui;
import fr.cleangang.cleanfishing.item.GameItem;
import fr.cleangang.cleanfishing.item.ItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Clic droit sur les objets du jeu (dynamite, filet, codex, menu des îles...), uniquement pour les joueurs en partie. */
public final class ItemListener implements Listener {

    private final CleanFishingPlugin plugin;

    public ItemListener(CleanFishingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        Game game = plugin.games().of(player);
        if (game == null) {
            return;
        }
        ItemStack item = event.getItem();
        String tag = plugin.items().tag(item);
        if (tag == null) {
            return;
        }
        if (ItemFactory.TAG_ROD.equals(tag)) {
            // la canne se lance normalement, mais on ne veut pas interagir avec les blocs
            event.setUseInteractedBlock(Event.Result.DENY);
            return;
        }
        event.setCancelled(true);
        switch (tag) {
            case ItemFactory.TAG_CODEX -> CodexGui.open(plugin, player, 0);
            case ItemFactory.TAG_MENU -> ArenaMenuGui.open(plugin, player);
            case ItemFactory.TAG_ADMIN -> {
                if (player.hasPermission("cleanfishing.admin")) {
                    AdminGui.openArenas(plugin, player);
                }
            }
            case ItemFactory.TAG_LEAVE -> game.leave(player, false);
            default -> {
                GameItem gameItem = GameItem.parse(tag);
                if (gameItem != null) {
                    game.useItem(player, gameItem, item);
                }
            }
        }
    }
}
