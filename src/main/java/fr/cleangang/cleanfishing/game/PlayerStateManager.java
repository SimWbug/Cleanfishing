package fr.cleangang.cleanfishing.game;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

/**
 * Sauvegarde l'état d'un joueur (inventaire, position, mode de jeu...) sur disque avant la partie
 * et le restaure ensuite, même après un crash ou une déconnexion en pleine partie.
 */
public final class PlayerStateManager {

    private final CleanFishingPlugin plugin;
    private final File dir;

    public PlayerStateManager(CleanFishingPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "states");
    }

    private File fileOf(UUID id) {
        return new File(dir, id + ".yml");
    }

    public boolean hasPending(UUID id) {
        return fileOf(id).exists();
    }

    public void save(Player player) {
        File file = fileOf(player.getUniqueId());
        if (file.exists()) {
            // un état d'origine est déjà sauvegardé : on ne l'écrase jamais
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        y.set("storage", encode(player.getInventory().getStorageContents()));
        y.set("armor", encode(player.getInventory().getArmorContents()));
        y.set("offhand", encode(new ItemStack[]{player.getInventory().getItemInOffHand()}));
        y.set("location", player.getLocation());
        y.set("gamemode", player.getGameMode().name());
        y.set("health", player.getHealth());
        y.set("food", player.getFoodLevel());
        y.set("saturation", (double) player.getSaturation());
        y.set("level", player.getLevel());
        y.set("exp", (double) player.getExp());
        try {
            dir.mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible de sauvegarder l'état de " + player.getName() + " : " + ex.getMessage());
        }
    }

    /** Remet le joueur comme avant la partie. Sans effet si aucun état n'est sauvegardé. */
    public void restore(Player player) {
        File file = fileOf(player.getUniqueId());
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        try {
            player.getInventory().clear();
            player.getInventory().setStorageContents(decode(y.getString("storage")));
            player.getInventory().setArmorContents(decode(y.getString("armor")));
            ItemStack[] off = decode(y.getString("offhand"));
            player.getInventory().setItemInOffHand(off.length > 0 && off[0] != null ? off[0] : new ItemStack(org.bukkit.Material.AIR));
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Inventaire illisible pour " + player.getName() + " (fichier conservé) : " + ex.getMessage());
            return;
        }
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        try {
            player.setGameMode(GameMode.valueOf(y.getString("gamemode", "SURVIVAL")));
        } catch (IllegalArgumentException ex) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0;
        player.setHealth(Math.max(1.0, Math.min(max, y.getDouble("health", 20.0))));
        player.setFoodLevel(y.getInt("food", 20));
        player.setSaturation((float) y.getDouble("saturation", 5.0));
        player.setLevel(y.getInt("level", 0));
        player.setExp((float) y.getDouble("exp", 0.0));
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        Location location = y.getLocation("location");
        if (location != null && location.getWorld() != null) {
            player.teleport(location);
        }
        file.delete();
    }

    private static String encode(ItemStack[] items) {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
    }

    private static ItemStack[] decode(String data) {
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }
        return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(data));
    }
}
