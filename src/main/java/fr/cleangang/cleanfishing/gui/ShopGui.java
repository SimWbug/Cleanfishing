package fr.cleangang.cleanfishing.gui;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.game.Game;
import fr.cleangang.cleanfishing.shop.ShopOffer;
import fr.cleangang.cleanfishing.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Boutique de Gertrude : jusqu'à 7 offres, payées avec la cagnotte de la journée. */
public final class ShopGui {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private ShopGui() {
    }

    public static final class Holder implements InventoryHolder {
        private final Game game;
        private Inventory inventory;

        Holder(Game game) {
            this.game = game;
        }

        public Game game() {
            return game;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(CleanFishingPlugin plugin, Game game, Player player) {
        Holder holder = new Holder(game);
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.mm("<gold>Boutique de Gertrude"));
        holder.inventory(inventory);
        render(plugin, game, inventory);
        player.openInventory(inventory);
    }

    public static void render(CleanFishingPlugin plugin, Game game, Inventory inventory) {
        String symbol = plugin.getConfig().getString("game.currency-symbol", "$");
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(m -> m.displayName(Component.text(" ")));
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        ItemStack info = new ItemStack(Material.GOLD_INGOT);
        info.editMeta(m -> {
            m.displayName(Text.item("<gold>Cagnotte : <green>" + game.pot() + symbol));
            m.lore(List.of(
                    Text.item("<gray>Palier du jour : <yellow>" + game.target() + symbol),
                    Text.item("<red>Chaque achat est prélevé sur la cagnotte !"),
                    Text.item("<dark_gray>La boutique change chaque journée.")));
        });
        inventory.setItem(4, info);

        List<ShopOffer> offers = game.offers();
        for (int i = 0; i < offers.size() && i < SLOTS.length; i++) {
            ShopOffer offer = offers.get(i);
            ItemStack stack = plugin.items().create(offer.item(), offer.amount());
            boolean affordable = game.pot() >= offer.price();
            stack.editMeta(m -> {
                List<Component> lore = new ArrayList<>();
                if (m.lore() != null) {
                    lore.addAll(m.lore());
                }
                lore.add(Component.empty());
                lore.add(Text.item("<gray>Prix : " + (affordable ? "<green>" : "<red>") + offer.price() + symbol));
                lore.add(Text.item(affordable ? "<yellow>Clic pour acheter" : "<red>Cagnotte insuffisante"));
                m.lore(lore);
            });
            inventory.setItem(SLOTS[i], stack);
        }
    }

    /** Index de l'offre pour un slot cliqué, ou -1. */
    public static int offerIndex(int slot) {
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }
}
