package fr.cleangang.cleanfishing.gui;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.game.Game;
import fr.cleangang.cleanfishing.npc.NpcManager;
import fr.cleangang.cleanfishing.pack.PackUi;
import fr.cleangang.cleanfishing.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface de Francis : état de la journée et vote pour la finir ou continuer à pêcher.
 * Avec le resource pack : portrait 3D, bulle de dialogue et tableau d'infos dessinés dans le fond.
 */
public final class FrancisGui {

    public static final int END_SLOT = 46;
    public static final int INFO_SLOT = 49;
    public static final int KEEP_SLOT = 52;
    private static final int HEAD_SLOT = 10;
    private static final int BUBBLE_SLOT = 13;
    private static final TextColor BUBBLE_COLOR = TextColor.color(0x2A2A35);

    private FrancisGui() {
    }

    public static final class Holder implements InventoryHolder {
        private final Game game;
        private final boolean pack;
        private final String line;
        private Inventory inventory;

        Holder(Game game, boolean pack, String line) {
            this.game = game;
            this.pack = pack;
            this.line = line;
        }

        public Game game() {
            return game;
        }

        public boolean pack() {
            return pack;
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
        boolean pack = plugin.pack().uses(player);
        String line = game.francisLine(player);
        Holder holder = new Holder(game, pack, line);
        Component title = pack
                ? PackUi.title(PackUi.FRANCIS_BACKGROUND, packLines(plugin, game, line))
                : Text.mm("<gold>Francis, pêcheur des mers");
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.inventory(inventory);
        render(plugin, game, player, inventory);
        player.openInventory(inventory);
    }

    /** Rouvre l'interface (mode pack : la bulle et le tableau font partie du titre). */
    public static void reopen(CleanFishingPlugin plugin, Game game, Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.games().of(player) == game && game.state() == Game.State.RUNNING) {
                open(plugin, game, player);
            }
        });
    }

    private static List<PackUi.Line> packLines(CleanFishingPlugin plugin, Game game, String line) {
        String symbol = plugin.getConfig().getString("game.currency-symbol", "$");
        boolean reached = game.pot() >= game.target();
        List<PackUi.Line> lines = new ArrayList<>(PackUi.bubble(line, BUBBLE_COLOR));
        int x1 = 70;
        int x2 = 165;
        lines.add(new PackUi.Line(4, "Palier : " + game.target() + symbol, TextColor.color(0xF0E6C8), x1, x2, true));
        lines.add(new PackUi.Line(5, "Cagnotte : " + game.pot() + symbol,
                reached ? TextColor.color(0x8CE08C) : TextColor.color(0xFFD27A), x1, x2, true));
        lines.add(new PackUi.Line(6, Text.clock(game.secondsLeft()) + "  -  votes " + game.endVotes() + "/" + game.endVotesRequired(),
                TextColor.color(0xC8DCF0), x1, x2, true));
        return lines;
    }

    public static void render(CleanFishingPlugin plugin, Game game, Player player, Inventory inventory) {
        String symbol = plugin.getConfig().getString("game.currency-symbol", "$");
        Holder holder = inventory.getHolder() instanceof Holder h ? h : null;
        boolean pack = holder != null && holder.pack;
        boolean reached = game.pot() >= game.target();
        boolean voted = game.hasVoted(player);

        inventory.clear();
        if (!pack) {
            ItemStack filler = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
            filler.editMeta(m -> m.displayName(Component.empty()));
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, filler);
            }
            ItemStack head = plugin.npcs().headFor(NpcManager.ROLE_FRANCIS);
            head.editMeta(m -> m.displayName(Text.item("<gold><bold>Francis")));
            inventory.setItem(HEAD_SLOT, head);
            ItemStack bubble = new ItemStack(Material.WRITABLE_BOOK);
            String line = holder != null ? holder.line : game.francisLine(player);
            bubble.editMeta(m -> {
                m.displayName(Text.item("<gold>Francis dit :"));
                m.lore(ShopGui.wrapLore(line));
            });
            inventory.setItem(BUBBLE_SLOT, bubble);
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
            if (plugin.getConfig().getDouble("game.carry-over-percent", 0) <= 0 || game.hardcore()) {
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
