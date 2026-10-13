package fr.cleangang.cleanfishing.gui;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.progress.Achievement;
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

/** Succès et titres : la liste de tous les succès, et le choix du titre affiché devant le pseudo en partie. */
public final class TitleGui {

    public static final int NONE_SLOT = 49;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private TitleGui() {
    }

    public static final class Holder implements InventoryHolder {
        private Inventory inventory;

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    public static void open(CleanFishingPlugin plugin, Player player) {
        Holder holder = new Holder();
        UUID id = player.getUniqueId();
        Inventory inventory = Bukkit.createInventory(holder, 54, Text.mm("<gold>Succès & titres <dark_gray>("
                + plugin.codex().achievementCount(id) + "/" + Achievement.values().length + ")"));
        holder.inventory(inventory);
        render(plugin, player, inventory);
        player.openInventory(inventory);
    }

    public static void render(CleanFishingPlugin plugin, Player player, Inventory inventory) {
        UUID id = player.getUniqueId();
        Achievement current = plugin.codex().title(id);
        inventory.clear();
        Achievement[] all = Achievement.values();
        for (int i = 0; i < all.length && i < 45; i++) {
            Achievement a = all[i];
            boolean unlocked = plugin.codex().hasAchievement(id, a);
            boolean equipped = a == current;
            ItemStack stack = new ItemStack(unlocked ? a.icon() : Material.GRAY_DYE);
            stack.editMeta(m -> {
                m.displayName(Text.item(unlocked ? a.title() + (equipped ? " <green>✔" : "") : "<dark_gray>??? <gray>(" + plainTitle(a) + ")"));
                List<Component> lore = new ArrayList<>();
                lore.add(Text.item("<gray>" + Text.esc(a.description())));
                lore.add(Component.empty());
                if (unlocked) {
                    lore.add(Text.item("<gray>Débloqué le <white>" + DATE.format(Instant.ofEpochMilli(plugin.codex().unlockedAt(id, a)))));
                    lore.add(Text.item(equipped ? "<green>Titre équipé" : "<yellow>Clic pour l'afficher devant ton pseudo"));
                } else {
                    lore.add(Text.item("<red>Pas encore débloqué"));
                }
                m.lore(lore);
                if (equipped) {
                    m.setEnchantmentGlintOverride(true);
                }
            });
            inventory.setItem(i, stack);
        }
        ItemStack none = new ItemStack(Material.BARRIER);
        none.editMeta(m -> {
            m.displayName(Text.item("<red>Aucun titre"));
            m.lore(List.of(Text.item(current == null ? "<green>Aucun titre affiché" : "<yellow>Clic pour retirer ton titre")));
        });
        inventory.setItem(NONE_SLOT, none);
    }

    private static String plainTitle(Achievement a) {
        return a.title().replaceAll("<[^>]+>", "");
    }

    /** Succès affiché dans ce slot, ou null. */
    public static Achievement at(int slot) {
        Achievement[] all = Achievement.values();
        return slot >= 0 && slot < all.length && slot < 45 ? all[slot] : null;
    }
}
