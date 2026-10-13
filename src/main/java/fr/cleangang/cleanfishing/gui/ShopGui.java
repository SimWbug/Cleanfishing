package fr.cleangang.cleanfishing.gui;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.game.Game;
import fr.cleangang.cleanfishing.game.Upgrade;
import fr.cleangang.cleanfishing.npc.NpcManager;
import fr.cleangang.cleanfishing.pack.PackUi;
import fr.cleangang.cleanfishing.shop.ShopOffer;
import fr.cleangang.cleanfishing.util.Fx;
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
 * Boutique de Gertrude : son portrait, sa bulle de dialogue, l'étal de 5 articles (dont une promotion et un article
 * rare), le bouton pour changer l'étal et les améliorations permanentes.
 * <p>
 * Deux présentations : « classique » (vitres colorées, tête de Gertrude) et « resource pack » (fond illustré avec
 * portrait 3D et texte dans la bulle) pour les joueurs qui ont chargé le pack.
 * <pre>
 *  rangée 0-1 : portrait (0-2) + bulle (3-8)        [classique : cagnotte au slot 4, chaînes 12-16]
 *  rangée 2   : les 5 articles (21-25)
 *  rangée 3   : leurs étiquettes de prix (30-34)
 *  rangée 4   : cagnotte (36 pack / 4 classique), relancer (40), bulle (37, classique)
 *  rangée 5   : améliorations (50-52), fermer (53)
 * </pre>
 */
public final class ShopGui {

    public static final int REROLL_SLOT = 40;
    public static final int CLOSE_SLOT = 53;
    public static final int[] UPGRADE_SLOTS = {50, 51, 52};

    private static final int[] CARD_SLOTS = {21, 22, 23, 24, 25};
    private static final int[] PRICE_SLOTS = {30, 31, 32, 33, 34};
    private static final int[] CHAIN_SLOTS = {12, 13, 14, 15, 16};
    private static final int[] FRAME_SLOTS = {9, 10, 11, 18, 20, 27, 28, 29};
    private static final int HEAD_SLOT = 19;
    private static final int BUBBLE_SLOT = 37;
    private static final int POT_SLOT_CLASSIC = 4;
    private static final int POT_SLOT_PACK = 36;
    private static final TextColor BUBBLE_COLOR = TextColor.color(0x3B2A1A);

    private ShopGui() {
    }

    public static final class Holder implements InventoryHolder {
        private final Game game;
        private final boolean pack;
        private String line;
        private Inventory inventory;

        Holder(Game game, boolean pack, String line) {
            this.game = game;
            this.pack = pack;
            this.line = line;
        }

        public Game game() {
            return game;
        }

        /** Vrai si cette fenêtre utilise le fond du resource pack. */
        public boolean pack() {
            return pack;
        }

        /** Phrase de la bulle (en mode classique : appliquée au prochain affichage). */
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

    public static void open(CleanFishingPlugin plugin, Game game, Player player, String line) {
        boolean pack = plugin.pack().uses(player);
        Holder holder = new Holder(game, pack, line);
        Component title = pack
                ? PackUi.title(PackUi.SHOP_BACKGROUND, PackUi.bubble(line, BUBBLE_COLOR))
                : Text.mm("<gold><bold>⚓ Chez Gertrude");
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.inventory(inventory);
        render(plugin, game, player, inventory);
        player.openInventory(inventory);
        Fx.sound(player, "entity.villager.trade", 1f, 1.1f);
    }

    /** Rouvre la boutique avec une nouvelle réplique (nécessaire en mode pack : la bulle fait partie du titre). */
    public static void reopen(CleanFishingPlugin plugin, Game game, Player player, String line) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && plugin.games().of(player) == game
                    && (game.state() == Game.State.RUNNING || game.state() == Game.State.INTERMISSION)) {
                open(plugin, game, player, line);
            }
        });
    }

    public static int potSlot(Holder holder) {
        return holder.pack ? POT_SLOT_PACK : POT_SLOT_CLASSIC;
    }

    public static void render(CleanFishingPlugin plugin, Game game, Player viewer, Inventory inventory) {
        String symbol = plugin.getConfig().getString("game.currency-symbol", "$");
        Holder holder = inventory.getHolder() instanceof Holder h ? h : null;
        boolean pack = holder != null && holder.pack;
        int pot = game.pot();

        inventory.clear();
        if (!pack) {
            ItemStack dark = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, dark);
            }
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
            ItemStack bubble = new ItemStack(Material.WRITABLE_BOOK);
            String line = holder != null ? holder.line : game.gertrudeLine();
            bubble.editMeta(m -> {
                m.displayName(Text.item("<gold>Gertrude dit :"));
                m.lore(wrapLore(line));
            });
            inventory.setItem(BUBBLE_SLOT, bubble);
        }

        // cagnotte
        ItemStack info = new ItemStack(Material.GOLD_INGOT);
        info.editMeta(m -> {
            m.displayName(Text.item("<gold>Cagnotte : <green>" + pot + symbol));
            m.lore(List.of(
                    Text.item("<gray>Palier du jour : <yellow>" + game.target() + symbol + (pot >= game.target() ? " <green>✔" : " <red>✘")),
                    Text.item(pot >= game.target()
                            ? "<gray>Marge : <green>" + (pot - game.target()) + symbol
                            : "<gray>Il manque : <yellow>" + (game.target() - pot) + symbol),
                    Component.empty(),
                    Text.item("<red>Chaque achat est prélevé sur la cagnotte !")));
        });
        inventory.setItem(pack ? POT_SLOT_PACK : POT_SLOT_CLASSIC, info);

        // étal : 5 articles
        List<ShopOffer> offers = game.offers();
        for (int i = 0; i < CARD_SLOTS.length; i++) {
            if (i >= offers.size()) {
                if (!pack) {
                    inventory.setItem(CARD_SLOTS[i], pane(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>Rien ici"));
                }
                continue;
            }
            ShopOffer offer = offers.get(i);
            int cost = game.priceFor(viewer, offer.price());
            boolean affordable = pot >= cost;
            if (!pack) {
                inventory.setItem(CHAIN_SLOTS[i], pane(chain(), " "));
            }
            inventory.setItem(CARD_SLOTS[i], card(plugin, offer, cost, affordable, symbol, pot));
            inventory.setItem(PRICE_SLOTS[i], priceTag(offer, cost, affordable, symbol, pot));
        }

        // changer l'étal
        int price = game.rerollPrice();
        int left = game.rerollsLeft();
        boolean canReroll = left != 0 && pot >= price;
        ItemStack reroll = new ItemStack(left == 0 ? Material.BARRIER : Material.ENDER_EYE);
        reroll.editMeta(m -> {
            m.displayName(Text.item(left == 0 ? "<red><bold>Réserves épuisées" : "<aqua><bold>Faire changer l'étal"));
            List<Component> lore = new ArrayList<>();
            lore.add(Text.item("<gray>Gertrude pioche de nouveaux articles."));
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

        // améliorations permanentes (propres au joueur qui regarde)
        Upgrade[] upgrades = Upgrade.values();
        for (int i = 0; i < upgrades.length && i < UPGRADE_SLOTS.length; i++) {
            inventory.setItem(UPGRADE_SLOTS[i], upgradeItem(plugin, game, viewer, upgrades[i], symbol, pot));
        }

        ItemStack close = new ItemStack(Material.OAK_DOOR);
        close.editMeta(m -> m.displayName(Text.item("<yellow>Sortir de la boutique")));
        inventory.setItem(CLOSE_SLOT, close);
    }

    private static ItemStack card(CleanFishingPlugin plugin, ShopOffer offer, int cost, boolean affordable, String symbol, int pot) {
        if (offer.soldOut()) {
            ItemStack sold = new ItemStack(Material.BARRIER);
            sold.editMeta(m -> {
                m.displayName(Text.item("<red><bold>VENDU"));
                m.lore(List.of(Text.item("<gray>" + offer.item().displayName() + " <gray>est parti."),
                        Text.item("<dark_gray>Revient peut-être demain...")));
            });
            return sold;
        }
        ItemStack stack = plugin.items().create(offer.item(), offer.amount());
        stack.editMeta(m -> {
            List<Component> lore = new ArrayList<>();
            if (offer.rare()) {
                lore.add(Text.item("<gold><bold>★ ARTICLE RARE ★"));
            } else if (offer.discounted()) {
                lore.add(Text.item("<green><bold>PROMO -" + offer.discountPercent() + "% <dark_gray>aujourd'hui seulement"));
            }
            if (m.lore() != null) {
                lore.addAll(m.lore());
            }
            lore.add(Component.empty());
            lore.add(Text.item("<gray>Quantité : <white>" + offer.amount()));
            if (cost < offer.basePrice()) {
                lore.add(Text.item("<gray>Prix : <dark_gray><strikethrough>" + offer.basePrice() + symbol + "</strikethrough> "
                        + (affordable ? "<green>" : "<red>") + cost + symbol));
            } else {
                lore.add(Text.item("<gray>Prix : " + (affordable ? "<green>" : "<red>") + cost + symbol));
            }
            if (offer.stock() > 0) {
                lore.add(Text.item("<dark_gray>Lot unique : une fois acheté, il disparaît de l'étal."));
            }
            lore.add(Text.item(affordable ? "<yellow>Clic pour acheter" : "<red>Il manque " + (cost - pot) + symbol));
            m.lore(lore);
            if (offer.rare() || offer.discounted()) {
                m.setEnchantmentGlintOverride(true);
            }
        });
        return stack;
    }

    private static ItemStack priceTag(ShopOffer offer, int cost, boolean affordable, String symbol, int pot) {
        Material material = offer.soldOut() ? Material.GRAY_STAINED_GLASS_PANE
                : offer.rare() ? Material.ORANGE_STAINED_GLASS_PANE
                : affordable ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        ItemStack tag = new ItemStack(material);
        tag.editMeta(m -> {
            if (offer.soldOut()) {
                m.displayName(Text.item("<gray>Vendu"));
                return;
            }
            String promo = offer.discounted() ? " <dark_green>-" + offer.discountPercent() + "%" : offer.rare() ? " <gold>★" : "";
            m.displayName(Text.item((affordable ? "<green><bold>" : "<red><bold>") + cost + symbol + "</bold>" + promo));
            m.lore(List.of(Text.item(affordable ? "<yellow>Clic pour acheter" : "<gray>Il manque <red>" + (cost - pot) + symbol)));
        });
        return tag;
    }

    private static ItemStack upgradeItem(CleanFishingPlugin plugin, Game game, Player viewer, Upgrade upgrade, String symbol, int pot) {
        int level = game.upgradeLevel(viewer, upgrade);
        int max = upgrade.maxLevel(plugin.getConfig());
        int base = upgrade.nextPrice(plugin.getConfig(), level);
        int price = base < 0 ? -1 : game.priceFor(viewer, base);
        ItemStack stack = new ItemStack(upgrade.icon());
        stack.editMeta(m -> {
            m.displayName(Text.item(upgrade.displayName() + " <gray>niv. <white>" + level + "<gray>/" + max));
            List<Component> lore = new ArrayList<>();
            for (String line : upgrade.lore()) {
                lore.add(Text.item(line));
            }
            lore.add(Text.item("<dark_gray>Permanent pour toute la partie, rien que pour toi."));
            lore.add(Component.empty());
            if (price < 0) {
                lore.add(Text.item("<green>Niveau maximum atteint."));
            } else {
                lore.add(Text.item("<gray>Niveau " + (level + 1) + " : " + (pot >= price ? "<green>" : "<red>") + price + symbol));
                lore.add(Text.item(pot >= price ? "<yellow>Clic pour acheter" : "<red>Cagnotte insuffisante"));
            }
            m.lore(lore);
            if (level > 0) {
                m.setEnchantmentGlintOverride(true);
            }
        });
        stack.setAmount(Math.max(1, level));
        return stack;
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

    /** Amélioration pour un slot cliqué, ou null. */
    public static Upgrade upgradeAt(int slot) {
        Upgrade[] upgrades = Upgrade.values();
        for (int i = 0; i < UPGRADE_SLOTS.length && i < upgrades.length; i++) {
            if (UPGRADE_SLOTS[i] == slot) {
                return upgrades[i];
            }
        }
        return null;
    }

    /** La chaîne de fer a été renommée selon les versions (CHAIN / IRON_CHAIN) : on cherche par nom. */
    private static Material chain() {
        for (String name : new String[]{"IRON_CHAIN", "CHAIN"}) {
            Material m = Material.matchMaterial(name);
            if (m != null) {
                return m;
            }
        }
        return Material.IRON_BARS;
    }

    private static ItemStack pane(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(m -> m.displayName(name.isBlank() ? Component.text(" ") : Text.item(name)));
        return stack;
    }

    /** Coupe une phrase en lignes d'environ 34 caractères (infobulle). */
    static List<Component> wrapLore(String text) {
        List<Component> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() + word.length() > 34 && !current.isEmpty()) {
                lines.add(Text.item("<white>" + Text.esc(current.toString())));
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            lines.add(Text.item("<white>" + Text.esc(current.toString())));
        }
        return lines;
    }
}
