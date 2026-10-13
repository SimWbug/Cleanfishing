package fr.cleangang.cleanfishing.gui;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.game.Game;
import fr.cleangang.cleanfishing.npc.NpcManager;
import fr.cleangang.cleanfishing.shop.ShopOffer;
import fr.cleangang.cleanfishing.util.Fx;
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
import java.util.Random;

/**
 * Boutique de Gertrude : portrait de la tavernière à gauche (sa tête avec son skin), son étal de 5 articles à droite,
 * payés avec la cagnotte, et un bouton pour faire changer l'étal contre une part de la cagnotte.
 * <pre>
 *  . . . . I . . . .     I = cagnotte
 *  P P P c c c c c .     P = portrait de Gertrude, c = chaînes
 *  P G P a a a a a .     G = sa tête, a = les 5 articles
 *  P P P $ $ $ $ $ .     $ = étiquettes de prix (cliquables)
 *  . B . . R . . . .     B = bulle de dialogue, R = changer l'étal
 *  . . . . X . . . .     X = fermer
 * </pre>
 */
public final class ShopGui {

    public static final int REROLL_SLOT = 40;
    public static final int CLOSE_SLOT = 49;

    private static final int[] CARD_SLOTS = {21, 22, 23, 24, 25};
    private static final int[] PRICE_SLOTS = {30, 31, 32, 33, 34};
    private static final int[] CHAIN_SLOTS = {12, 13, 14, 15, 16};
    private static final int[] FRAME_SLOTS = {9, 10, 11, 18, 20, 27, 28, 29};
    private static final int HEAD_SLOT = 19;
    private static final int POT_SLOT = 4;
    private static final int BUBBLE_SLOT = 37;

    private static final List<String> GREETINGS = List.of(
            "Entre, entre ! J'ai ce qu'il te faut pour les gros poissons.",
            "Du neuf sur l'étal aujourd'hui, mon grand !",
            "La cagnotte parle, moi je vends.",
            "Attention aux prix, la mer ne rembourse pas !",
            "Dynamite, filets... tout ce qu'un pêcheur rêve d'avoir.");

    private static final Random RANDOM = new Random();

    private ShopGui() {
    }

    public static final class Holder implements InventoryHolder {
        private final Game game;
        private Inventory inventory;
        private String line = GREETINGS.get(RANDOM.nextInt(GREETINGS.size()));

        Holder(Game game) {
            this.game = game;
        }

        public Game game() {
            return game;
        }

        /** Phrase affichée dans la bulle de Gertrude. */
        public void say(String text) {
            this.line = text;
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
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.mm("<gold><bold>⚓ Chez Gertrude"));
        holder.inventory(inventory);
        render(plugin, game, inventory);
        player.openInventory(inventory);
        Fx.sound(player, "entity.villager.trade", 1f, 1.1f);
    }

    public static void render(CleanFishingPlugin plugin, Game game, Inventory inventory) {
        String symbol = plugin.getConfig().getString("game.currency-symbol", "$");
        Holder holder = inventory.getHolder() instanceof Holder h ? h : null;
        int pot = game.pot();

        // fond
        ItemStack dark = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, dark);
        }

        // cadre du portrait
        ItemStack frame = pane(Material.ORANGE_STAINED_GLASS_PANE, " ");
        for (int slot : FRAME_SLOTS) {
            inventory.setItem(slot, frame);
        }
        ItemStack head = plugin.npcs().headFor(NpcManager.ROLE_GERTRUDE);
        head.editMeta(m -> {
            m.displayName(Text.item("<gold><bold>Gertrude"));
            m.lore(List.of(Text.item("<gray>" + plugin.getConfig().getString("npc.gertrude.description", "La tavernière du cabanon."))));
        });
        inventory.setItem(HEAD_SLOT, head);

        // bulle de dialogue
        ItemStack bubble = new ItemStack(Material.WRITABLE_BOOK);
        String line = holder != null ? holder.line : GREETINGS.get(0);
        bubble.editMeta(m -> {
            m.displayName(Text.item("<gold>Gertrude dit :"));
            m.lore(wrap(line));
        });
        inventory.setItem(BUBBLE_SLOT, bubble);

        // cagnotte
        ItemStack info = new ItemStack(Material.GOLD_INGOT);
        info.editMeta(m -> {
            m.displayName(Text.item("<gold>Cagnotte : <green>" + pot + symbol));
            m.lore(List.of(
                    Text.item("<gray>Palier du jour : <yellow>" + game.target() + symbol + (pot >= game.target() ? " <green>✔" : " <red>✘")),
                    Text.item("<red>Chaque achat est prélevé sur la cagnotte !")));
        });
        inventory.setItem(POT_SLOT, info);

        // étal : 5 articles
        List<ShopOffer> offers = game.offers();
        for (int i = 0; i < CARD_SLOTS.length; i++) {
            if (i >= offers.size()) {
                inventory.setItem(CARD_SLOTS[i], pane(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>Rupture de stock"));
                continue;
            }
            ShopOffer offer = offers.get(i);
            boolean affordable = pot >= offer.price();
            inventory.setItem(CHAIN_SLOTS[i], pane(Material.CHAIN, " "));

            ItemStack stack = plugin.items().create(offer.item(), offer.amount());
            stack.editMeta(m -> {
                List<Component> lore = new ArrayList<>();
                if (m.lore() != null) {
                    lore.addAll(m.lore());
                }
                lore.add(Component.empty());
                lore.add(Text.item("<gray>Quantité : <white>" + offer.amount()));
                lore.add(Text.item(affordable ? "<yellow>Clic pour acheter" : "<red>Cagnotte insuffisante"));
                m.lore(lore);
            });
            inventory.setItem(CARD_SLOTS[i], stack);

            ItemStack tag = new ItemStack(affordable ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
            tag.editMeta(m -> {
                m.displayName(Text.item((affordable ? "<green><bold>" : "<red><bold>") + offer.price() + symbol));
                m.lore(List.of(Text.item(affordable ? "<yellow>Clic pour acheter" : "<gray>Il manque <red>" + (offer.price() - pot) + symbol)));
            });
            inventory.setItem(PRICE_SLOTS[i], tag);
        }

        // changer l'étal
        int price = game.rerollPrice();
        int left = game.rerollsLeft();
        boolean canReroll = left != 0 && pot >= price;
        ItemStack reroll = new ItemStack(left == 0 ? Material.BARRIER : Material.ENDER_EYE);
        reroll.editMeta(m -> {
            m.displayName(Text.item(left == 0 ? "<red><bold>Réserves épuisées" : "<aqua><bold>Faire changer l'étal"));
            List<Component> lore = new ArrayList<>();
            lore.add(Text.item("<gray>Gertrude pioche 5 nouveaux articles."));
            if (left != 0) {
                lore.add(Text.item("<gray>Prix : " + (canReroll ? "<green>" : "<red>") + price + symbol
                        + " <dark_gray>(+ cher à chaque fois)"));
            }
            if (left > 0) {
                lore.add(Text.item("<gray>Encore <white>" + left + "<gray> changement(s) aujourd'hui."));
            }
            lore.add(Component.empty());
            lore.add(Text.item(left == 0 ? "<red>Plus rien en réserve." : canReroll ? "<yellow>Clic" : "<red>Cagnotte insuffisante"));
            m.lore(lore);
        });
        inventory.setItem(REROLL_SLOT, reroll);

        ItemStack close = new ItemStack(Material.ARROW);
        close.editMeta(m -> m.displayName(Text.item("<yellow>Fermer")));
        inventory.setItem(CLOSE_SLOT, close);
    }

    /** Index de l'article pour un slot cliqué (carte ou étiquette de prix), ou -1. */
    public static int offerIndex(int slot) {
        for (int i = 0; i < CARD_SLOTS.length; i++) {
            if (CARD_SLOTS[i] == slot || PRICE_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private static ItemStack pane(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(m -> m.displayName(name.isBlank() ? Component.text(" ") : Text.item(name)));
        return stack;
    }

    /** Coupe une phrase en lignes d'environ 34 caractères. */
    private static List<Component> wrap(String text) {
        List<Component> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() + word.length() > 34 && current.length() > 0) {
                lines.add(Text.item("<white>" + Text.esc(current.toString())));
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0) {
            lines.add(Text.item("<white>" + Text.esc(current.toString())));
        }
        return lines;
    }
}
