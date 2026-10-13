package fr.cleangang.cleanfishing.gui;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.game.Game;
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

/** Interface de Francis : état de la journée et vote pour la finir ou continuer à pêcher. */
public final class FrancisGui {

    public static final int END_SLOT = 11;
    public static final int INFO_SLOT = 13;
    public static final int KEEP_SLOT = 15;

    private FrancisGui() {
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
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.mm("<gold>Francis, pêcheur des mers"));
        holder.inventory(inventory);
        render(plugin, game, player, inventory);
        player.openInventory(inventory);
    }

    public static void render(CleanFishingPlugin plugin, Game game, Player player, Inventory inventory) {
        String symbol = plugin.getConfig().getString("game.currency-symbol", "$");
        boolean reached = game.pot() >= game.target();
        boolean voted = game.hasVoted(player);

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(m -> m.displayName(Component.empty()));
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(INFO_SLOT, button(Material.CLOCK, "<gold>Journée " + game.day(),
                "<gray>Cagnotte : " + (reached ? "<green>" : "<yellow>") + game.pot() + symbol,
                "<gray>Palier : <yellow>" + game.target() + symbol + (reached ? " <green>✔" : " <red>✘"),
                "<gray>Temps restant : <white>" + Text.clock(game.secondsLeft()),
                "",
                "<gray>Vote pour finir : <white>" + game.endVotes() + "/" + game.endVotesRequired()));

        if (reached) {
            List<String> lore = new ArrayList<>();
            lore.add(voted ? "<green>Ton vote est enregistré." : "<gray>Vote pour rentrer au cabanon.");
            lore.add("<gray>La journée s'arrête quand l'équipe a voté.");
            if (plugin.getConfig().getDouble("game.carry-over-percent", 0) <= 0) {
                lore.add("");
                lore.add("<red>Dépense la cagnotte chez Gertrude :");
                lore.add("<red>ce qui reste ne sera pas conservé.");
            }
            lore.add("");
            lore.add(voted ? "<dark_gray>Pour annuler : « Continuer à pêcher »" : "<yellow>Clic pour voter");
            inventory.setItem(END_SLOT, button(voted ? Material.EMERALD_BLOCK : Material.EMERALD,
                    voted ? "<green><bold>Tu veux finir la journée" : "<green><bold>Finir la journée",
                    lore.toArray(new String[0])));
        } else {
            inventory.setItem(END_SLOT, button(Material.BARRIER, "<red>Palier non atteint",
                    "<gray>Il manque <yellow>" + (game.target() - game.pot()) + symbol + "<gray> pour finir la journée.",
                    "<gray>Francis refuse de rentrer les mains vides !"));
        }

        inventory.setItem(KEEP_SLOT, button(Material.FISHING_ROD, "<yellow><bold>Continuer à pêcher",
                voted ? "<gray>Retire ton vote et ferme le menu." : "<gray>Ferme le menu.",
                "",
                "<yellow>Clic"));
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(Text.item(name));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Text.item(line));
            }
            meta.lore(lines);
        });
        return stack;
    }
}
