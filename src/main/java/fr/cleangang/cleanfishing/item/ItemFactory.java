package fr.cleangang.cleanfishing.item;

import com.destroystokyo.paper.profile.ProfileProperty;
import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Fabrique et identifie les objets du plugin via une clé PDC propre à CleanFishing. */
public final class ItemFactory {

    public static final String TAG_ROD = "ROD";
    public static final String TAG_CODEX = "CODEX";
    public static final String TAG_MENU = "MENU";
    public static final String TAG_ADMIN = "ADMIN";
    public static final String TAG_LEAVE = "LEAVE";

    public static final int SLOT_ROD = 0;
    public static final int SLOT_MENU = 3;
    public static final int SLOT_ADMIN = 5;
    public static final int SLOT_LEAVE = 7;
    public static final int SLOT_CODEX = 8;

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
            meta.lore(List.of(Text.item("<gray>Ta canne fétiche. Elle ne casse jamais.")));
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
            meta.lore(List.of(
                    Text.item("<gray>Clic droit : ouvre ton codex."),
                    Text.item("<gray>Tous les poissons que tu as pêchés.")));
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, TAG_CODEX);
        });
        return stack;
    }

    /**
     * Tête de poisson du lobby : ouvre le choix des îles.
     * Si hotbar.menu-item.texture est renseigné, c'est une vraie tête à texture ; sinon l'item de repli
     * (hotbar.menu-item.fallback-material, poisson tropical par défaut).
     */
    public ItemStack menuItem() {
        String texture = plugin.getConfig().getString("hotbar.menu-item.texture", "");
        if (texture == null || texture.isBlank()) {
            return menuBase(fallbackMaterial());
        }
        ItemStack head = menuBase(Material.PLAYER_HEAD);
        try {
            head.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile()
                    .addProperty(new ProfileProperty("textures", texture.trim()))
                    .build());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("hotbar.menu-item.texture invalide, item de repli utilisé : " + ex.getMessage());
            return menuBase(fallbackMaterial());
        }
        return head;
    }

    private ItemStack menuBase(Material material) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(Text.item("<aqua><bold>Choisir une île"));
            meta.lore(List.of(
                    Text.item("<gray>Clic droit : liste des îles de pêche"),
                    Text.item("<gray>et changement d'île.")));
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, TAG_MENU);
        });
        return stack;
    }

    private Material fallbackMaterial() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("hotbar.menu-item.fallback-material", "TROPICAL_FISH"));
        return m != null && m.isItem() ? m : Material.TROPICAL_FISH;
    }

    public ItemStack adminItem() {
        ItemStack stack = new ItemStack(Material.NETHER_STAR);
        stack.editMeta(meta -> {
            meta.displayName(Text.item("<red><bold>Outils admin"));
            meta.lore(List.of(
                    Text.item("<gray>Clic droit : gérer les arènes"),
                    Text.item("<gray>(lancer, arrêter, définir les points...)")));
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, TAG_ADMIN);
        });
        return stack;
    }

    public ItemStack leaveItem() {
        ItemStack stack = new ItemStack(Material.RED_DYE);
        stack.editMeta(meta -> {
            meta.displayName(Text.item("<red>Quitter la partie"));
            meta.lore(List.of(Text.item("<gray>Clic droit : retour à ton inventaire.")));
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, TAG_LEAVE);
        });
        return stack;
    }

    /** Équipement de départ. En salle d'attente (lobby = true) : menu des îles et bouton quitter en plus. */
    public void giveKit(Player player, boolean lobby) {
        PlayerInventory inv = player.getInventory();
        inv.setItem(SLOT_ROD, rod());
        inv.setItem(SLOT_CODEX, codexBook());
        if (lobby) {
            inv.setItem(SLOT_MENU, menuItem());
            inv.setItem(SLOT_LEAVE, leaveItem());
        }
        if (player.hasPermission("cleanfishing.admin")) {
            inv.setItem(SLOT_ADMIN, adminItem());
        }
        inv.setHeldItemSlot(SLOT_ROD);
    }

    /** Retire les objets réservés à la salle d'attente (au début de la première journée). */
    public void stripLobbyItems(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            String tag = tag(inv.getItem(i));
            if (TAG_MENU.equals(tag) || TAG_LEAVE.equals(tag)) {
                inv.setItem(i, null);
            }
        }
    }

    /** Étiquette PDC de l'item (nom d'un GameItem, "ROD", "CODEX", "MENU", "ADMIN" ou "LEAVE"), ou null. */
    public String tag(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}
