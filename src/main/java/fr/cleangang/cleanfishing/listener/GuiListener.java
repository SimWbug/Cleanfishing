package fr.cleangang.cleanfishing.listener;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.game.Game;
import fr.cleangang.cleanfishing.gui.CodexGui;
import fr.cleangang.cleanfishing.gui.ShopGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/** Clics dans la boutique de Gertrude et le codex ; toute autre fenêtre est ignorée. */
public final class GuiListener implements Listener {

    private final CleanFishingPlugin plugin;

    public GuiListener(CleanFishingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (holder instanceof ShopGui.Holder shop) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            int index = ShopGui.offerIndex(event.getSlot());
            if (index < 0) {
                return;
            }
            Game game = shop.game();
            if (plugin.games().of(player) != game) {
                player.closeInventory();
                return;
            }
            game.buy(player, index);
            ShopGui.render(plugin, game, event.getView().getTopInventory());
        } else if (holder instanceof CodexGui.Holder codex) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            if (event.getSlot() == CodexGui.PREV_SLOT) {
                CodexGui.open(plugin, player, codex.page() - 1);
            } else if (event.getSlot() == CodexGui.NEXT_SLOT) {
                CodexGui.open(plugin, player, codex.page() + 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ShopGui.Holder || holder instanceof CodexGui.Holder) {
            event.setCancelled(true);
        }
    }
}
