package fr.cleangang.cleanfishing.listener;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.arena.Arena;
import fr.cleangang.cleanfishing.game.Game;
import fr.cleangang.cleanfishing.gui.AdminGui;
import fr.cleangang.cleanfishing.gui.ArenaMenuGui;
import fr.cleangang.cleanfishing.gui.CodexGui;
import fr.cleangang.cleanfishing.gui.FrancisGui;
import fr.cleangang.cleanfishing.gui.ShopGui;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/** Clics dans la boutique, le codex, le choix d'île et les outils admin ; toute autre fenêtre est ignorée. */
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
            Game game = shop.game();
            if (plugin.games().of(player) != game) {
                player.closeInventory();
                return;
            }
            int clicked = event.getSlot();
            if (clicked == ShopGui.CLOSE_SLOT) {
                Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
                return;
            }
            if (clicked == ShopGui.REROLL_SLOT) {
                shop.say(game.reroll(player)
                        ? "Voyons ce que j'ai dans l'arrière-boutique..."
                        : "Pas de quoi changer l'étal pour le moment.");
                ShopGui.render(plugin, game, event.getView().getTopInventory());
                return;
            }
            int index = ShopGui.offerIndex(clicked);
            if (index < 0) {
                return;
            }
            int before = game.pot();
            game.buy(player, index);
            shop.say(game.pot() < before ? "Merci mon grand, bonne pêche !" : "Reviens avec une cagnotte plus remplie.");
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
        } else if (holder instanceof ArenaMenuGui.Holder menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            if (event.getSlot() == menu.adminSlot() && player.hasPermission("cleanfishing.admin")) {
                Bukkit.getScheduler().runTask(plugin, () -> AdminGui.openArenas(plugin, player));
                return;
            }
            String name = menu.arenaAt(event.getSlot());
            Arena arena = name == null ? null : plugin.arenas().get(name);
            if (arena == null) {
                return;
            }
            // rejoindre / changer d'île modifie l'inventaire du joueur : on le fait après l'événement
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    ArenaMenuGui.select(plugin, player, arena);
                }
            });
        } else if (holder instanceof FrancisGui.Holder francis) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            Game game = francis.game();
            if (plugin.games().of(player) != game) {
                player.closeInventory();
                return;
            }
            int clicked = event.getSlot();
            if (clicked == FrancisGui.END_SLOT) {
                game.setEndVote(player, true);
            } else if (clicked == FrancisGui.KEEP_SLOT) {
                game.setEndVote(player, false);
                Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
                return;
            } else {
                return;
            }
            if (game.state() != Game.State.RUNNING) {
                Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            } else {
                FrancisGui.render(plugin, game, player, event.getView().getTopInventory());
            }
        } else if (holder instanceof AdminGui.Holder admin) {
            event.setCancelled(true);
            if (!player.hasPermission("cleanfishing.admin")) {
                player.closeInventory();
                return;
            }
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            int slot = event.getSlot();
            ClickType click = event.getClick();
            Bukkit.getScheduler().runTask(plugin, () -> AdminGui.handleClick(plugin, player, admin, slot, click));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ShopGui.Holder || holder instanceof CodexGui.Holder
                || holder instanceof ArenaMenuGui.Holder || holder instanceof AdminGui.Holder
                || holder instanceof FrancisGui.Holder) {
            event.setCancelled(true);
        }
    }
}
