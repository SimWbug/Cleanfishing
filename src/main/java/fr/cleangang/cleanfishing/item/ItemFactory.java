package fr.cleangang.cleanfishing.item;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Fabrique et identifie les objets du plugin via une clé PDC propre à CleanFishing. */
public final class ItemFactory {

    public static final String TAG_ROD = "ROD";
    public static final String TAG_CODEX = "CODEX";

    private final CleanFishingPlugin plugin;
    private final NamespacedKey key;

    public ItemFactory(CleanFishingPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "item");
    }

    public ItemStack create(GameItem gameItem, int amount) {
        ItemStack stack = new ItemStack(gameItem.material(), Math.max(1, amount));
        stack.editMeta(meta -> {
            meta.displayName(Text.item(gameItem.displayName()));
            meta.lore(gameItem.lore().stream().map(Text::item).toList());
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, gameItem.name());
        });
        return stack;
    }

    public ItemStack rod() {
        int lure = Math.max(0, Math.min(5, plugin.getConfig().getInt("game.rod-lure-level", 2)));
        ItemStack stack = new ItemStack(Material.FISHING_ROD);
        stack.editMeta(meta -> {
            meta.displayName(Text.item("<aqua>Canne de pêcheur"));
            meta.lore(java.util.List.of(Text.item("<gray>Ta canne fétiche. Elle ne casse jamais.")));
            meta.setUnbreakable(true);
            if (lure > 0) {
                meta.addEnchant(Enchantment.LURE, lure, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, TAG_ROD);
        });
        return stack;
    }

    public ItemStack codexBook() {
        ItemStack stack = new ItemStack(Material.BOOK);
        stack.editMeta(meta -> {
            meta.displayName(Text.item("<gold>Codex des poissons"));
            meta.lore(java.util.List.of(
                    Text.item("<gray>Clic droit : ouvre ton codex."),
                    Text.item("<gray>Tous les poissons que tu as pêchés.")));
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, TAG_CODEX);
        });
        return stack;
    }

    /** Étiquette PDC de l'item (nom d'un GameItem, "ROD" ou "CODEX"), ou null. */
    public String tag(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}
