package fr.cleangang.cleanfishing.listener;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.arena.Arena;
import fr.cleangang.cleanfishing.game.Game;
import fr.cleangang.cleanfishing.game.Role;
import fr.cleangang.cleanfishing.game.Upgrade;
import fr.cleangang.cleanfishing.gui.AdminGui;
import fr.cleangang.cleanfishing.gui.ArenaMenuGui;
import fr.cleangang.cleanfishing.gui.CodexGui;
import fr.cleangang.cleanfishing.gui.FrancisGui;
import fr.cleangang.cleanfishing.gui.LobbyGui;
import fr.cleangang.cleanfishing.gui.ShopGui;
import fr.cleangang.cleanfishing.gui.TitleGui;
import fr.cleangang.cleanfishing.progress.Achievement;
import fr.cleangang.cleanfishing.util.Fx;
import fr.cleangang.cleanfishing.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/** Clics dans les interfaces du plugin ; toute autre fenêtre est ignorée. */
public final class GuiListener implements Listener {

    private final CleanFishingPlugin plugin;

    public GuiListener(CleanFishingPlugin plugin) {
        this.plugin = plugin;
    }

    private static boolean ours(InventoryHolder holder) {
        return holder instanceof ShopGui.Holder || holder instanceof CodexGui.Holder
                || holder instanceof ArenaMenuGui.Holder || holder instanceof AdminGui.Holder
                || holder instanceof FrancisGui.Holder || holder instanceof LobbyGui.Holder
                || holder instanceof TitleGui.Holder;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!ours(holder) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getSlot();
        if (holder instanceof ShopGui.Holder shop) {
            shopClick(player, shop, slot);
        } else if (holder instanceof CodexGui.Holder codex) {
            if (slot == CodexGui.PREV_SLOT) {
                CodexGui.open(plugin, player, codex.page() - 1);
            } else if (slot == CodexGui.NEXT_SLOT) {
                CodexGui.open(plugin, player, codex.page() + 1);
            } else if (slot == CodexGui.TITLES_SLOT) {
                Bukkit.getScheduler().runTask(plugin, () -> TitleGui.open(plugin, player));
            }
        } else if (holder instanceof TitleGui.Holder) {
            titleClick(player, slot);
        } else if (holder instanceof ArenaMenuGui.Holder menu) {
            if (slot == menu.adminSlot() && player.hasPermission("cleanfishing.admin")) {
                Bukkit.getScheduler().runTask(plugin, () -> AdminGui.openArenas(plugin, player));
                return;
            }
            String name = menu.arenaAt(slot);
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
        } else if (holder instanceof LobbyGui.Holder lobby) {
            Game game = lobby.game();
            if (plugin.games().of(player) != game || game.state() != Game.State.LOBBY) {
                Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
                return;
            }
            Role role = LobbyGui.roleAt(slot);
            if (role != null) {
                game.setRole(player, role);
            } else if (slot == LobbyGui.HARDCORE_SLOT && plugin.getConfig().getBoolean("hardcore.enabled", true)) {
                game.toggleHardcoreVote(player);
            } else {
                return;
            }
            LobbyGui.render(plugin, game, player, event.getView().getTopInventory());
        } else if (holder instanceof FrancisGui.Holder francis) {
            francisClick(player, francis, slot);
        } else if (holder instanceof AdminGui.Holder admin) {
            if (!player.hasPermission("cleanfishing.admin")) {
                Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
                return;
            }
            ClickType click = event.getClick();
            Bukkit.getScheduler().runTask(plugin, () -> AdminGui.handleClick(plugin, player, admin, slot, click));
        }
    }

    private void shopClick(Player player, ShopGui.Holder shop, int slot) {
        Game game = shop.game();
        if (plugin.games().of(player) != game) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        if (slot == ShopGui.CLOSE_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        String reply;
        if (slot == ShopGui.REROLL_SLOT) {
            reply = game.reroll(player) ? game.gertrudeSays("reroll") : game.gertrudeSays("broke");
        } else {
            Upgrade upgrade = ShopGui.upgradeAt(slot);
            int index = ShopGui.offerIndex(slot);
            if (upgrade != null) {
                reply = game.buyUpgrade(player, upgrade) ? game.gertrudeSays("thanks") : game.gertrudeSays("broke");
            } else if (index >= 0) {
                boolean soldOut = index < game.offers().size() && game.offers().get(index).soldOut();
                reply = game.buy(player, index) ? game.gertrudeSays("thanks") : game.gertrudeSays(soldOut ? "soldout" : "broke");
            } else {
                return;
            }
        }
        if (shop.pack()) {
            ShopGui.reopen(plugin, game, player, reply);
        } else {
            shop.say(reply);
            ShopGui.render(plugin, game, player, player.getOpenInventory().getTopInventory());
        }
    }

    private void francisClick(Player player, FrancisGui.Holder francis, int slot) {
        Game game = francis.game();
        if (plugin.games().of(player) != game) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        if (slot == FrancisGui.END_SLOT) {
            game.setEndVote(player, true);
        } else if (slot == FrancisGui.KEEP_SLOT) {
            game.setEndVote(player, false);
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        } else if (slot != FrancisGui.INFO_SLOT) {
            return;
        }
        if (game.state() != Game.State.RUNNING) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
        } else if (francis.pack()) {
            FrancisGui.reopen(plugin, game, player);
        } else {
            FrancisGui.render(plugin, game, player, player.getOpenInventory().getTopInventory());
        }
    }

    private void titleClick(Player player, int slot) {
        Achievement chosen;
        if (slot == TitleGui.NONE_SLOT) {
            chosen = null;
        } else {
            chosen = TitleGui.at(slot);
            if (chosen == null) {
                return;
            }
            if (!plugin.codex().hasAchievement(player.getUniqueId(), chosen)) {
                Fx.sound(player, "entity.villager.no", 1f, 1f);
                return;
            }
        }
        plugin.codex().title(player.getUniqueId(), chosen);
        player.sendMessage(Text.msg(chosen == null ? "Titre retiré." : "Titre équipé : " + chosen.title()));
        Fx.sound(player, "item.armor.equip_leather", 1f, 1.2f);
        Game game = plugin.games().of(player);
        if (game != null) {
            game.refreshNametags();
        }
        TitleGui.render(plugin, player, player.getOpenInventory().getTopInventory());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (ours(event.getView().getTopInventory().getHolder())) {
            event.setCancelled(true);
        }
    }
}
