package fr.cleangang.cleanfishing.gui;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.fish.FishType;
import fr.cleangang.cleanfishing.fish.Rarity;
import fr.cleangang.cleanfishing.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Codex : tous les poissons du catalogue, révélés au fur et à mesure des prises du joueur. */
public final class CodexGui {

    public static final int PREV_SLOT = 45;
    public static final int NEXT_SLOT = 53;
    public static final int TITLES_SLOT = 47;
    private static final int PER_PAGE = 45;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private CodexGui() {
    }

    public static final class Holder implements InventoryHolder {
        private final int page;
        private Inventory inventory;

        Holder(int page) {
            this.page = page;
        }

        public int page() {
            return page;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(CleanFishingPlugin plugin, Player player, int requestedPage) {
        List<FishType> list = plugin.fish().sorted();
        int pages = Math.max(1, (int) Math.ceil(list.size() / (double) PER_PAGE));
        int page = Math.max(0, Math.min(pages - 1, requestedPage));

        Holder holder = new Holder(page);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Text.mm("<gold>Codex des poissons <dark_gray>(" + (page + 1) + "/" + pages + ")"));
        holder.inventory(inventory);

        UUID id = player.getUniqueId();
        for (int i = 0; i < PER_PAGE; i++) {
            int index = page * PER_PAGE + i;
            if (index >= list.size()) {
                break;
            }
            inventory.setItem(i, entry(plugin, id, list.get(index)));
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(m -> m.displayName(Component.text(" ")));
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, filler);
        }
        if (page > 0) {
            inventory.setItem(PREV_SLOT, arrow("<yellow>Page précédente"));
        }
        if (page < pages - 1) {
            inventory.setItem(NEXT_SLOT, arrow("<yellow>Page suivante"));
        }
        inventory.setItem(49, summary(plugin, id, list));
        ItemStack titles = new ItemStack(Material.NAME_TAG);
        int unlocked = plugin.codex().achievementCount(id);
        titles.editMeta(m -> {
            m.displayName(Text.item("<gold>Succès & titres"));
            m.lore(List.of(
                    Text.item("<gray>Débloqués : <white>" + unlocked + "/" + fr.cleangang.cleanfishing.progress.Achievement.values().length),
                    Text.item("<gray>Choisis le titre affiché devant ton pseudo."),
                    Component.empty(),
                    Text.item("<yellow>Clic")));
        });
        inventory.setItem(TITLES_SLOT, titles);
        player.openInventory(inventory);
    }

    private static ItemStack entry(CleanFishingPlugin plugin, UUID id, FishType fish) {
        String symbol = plugin.getConfig().getString("game.currency-symbol", "$");
        if (!plugin.codex().discovered(id, fish.id())) {
            ItemStack unknown = new ItemStack(Material.GRAY_DYE);
            unknown.editMeta(m -> {
                m.displayName(Text.item("<dark_gray><bold>???"));
                m.lore(List.of(
                        Text.item("<gray>Rareté : " + fish.rarity().tag() + fish.rarity().label()),
                        Text.item("<dark_gray>Pas encore pêché.")));
            });
            return unknown;
        }
        ItemStack stack = fish.icon();
        int count = plugin.codex().count(id, fish.id());
        int best = plugin.codex().best(id, fish.id());
        long first = plugin.codex().first(id, fish.id());
        stack.editMeta(m -> {
            m.displayName(Text.item(fish.coloredName()));
            List<Component> lore = new ArrayList<>();
            lore.add(Text.item("<gray>Rareté : " + fish.rarity().tag() + fish.rarity().label()));
            lore.add(Text.item("<gray>Valeur de base : <green>" + fish.value() + symbol));
            lore.add(Text.item("<gray>Taille : <white>" + fish.minSize() + " - " + fish.maxSize() + " cm"));
            lore.add(Component.empty());
            lore.add(Text.item("<gray>Pêché : <white>" + count + " fois"));
            lore.add(Text.item("<gray>Record : <aqua>" + best + " cm"));
            if (first > 0) {
                lore.add(Text.item("<gray>Première prise : <white>" + DATE.format(Instant.ofEpochMilli(first))));
            }
            if (!fish.description().isBlank()) {
                lore.add(Component.empty());
                lore.add(Text.item("<dark_gray><italic>" + Text.esc(fish.description())));
            }
            m.lore(lore);
        });
        return stack;
    }

    private static ItemStack arrow(String name) {
        ItemStack arrow = new ItemStack(Material.ARROW);
        arrow.editMeta(m -> m.displayName(Text.item(name)));
        return arrow;
    }

    private static ItemStack summary(CleanFishingPlugin plugin, UUID id, List<FishType> list) {
        int total = list.size();
        int found = plugin.codex().discoveredCount(id);
        int legendaryTotal = 0;
        int legendaryFound = 0;
        for (FishType f : list) {
            if (f.rarity() == Rarity.LEGENDARY) {
                legendaryTotal++;
                if (plugin.codex().discovered(id, f.id())) {
                    legendaryFound++;
                }
            }
        }
        int percent = total == 0 ? 0 : (int) Math.round(found * 100.0 / total);
        int lt = legendaryTotal;
        int lf = legendaryFound;
        ItemStack book = new ItemStack(Material.BOOK);
        book.editMeta(m -> {
            m.displayName(Text.item("<gold>Ta progression"));
            m.lore(List.of(
                    Text.item("<gray>Poissons découverts : <white>" + found + "/" + total + " <dark_gray>(" + percent + "%)"),
                    Text.item("<gray>Légendaires : <gold>" + lf + "/" + lt)));
        });
        return book;
    }
}
