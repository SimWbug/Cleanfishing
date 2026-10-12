package fr.cleangang.cleanfishing.npc;

import com.destroystokyo.paper.profile.ProfileProperty;
import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.arena.Arena;
import fr.cleangang.cleanfishing.util.Text;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * PNJ Gertrude (boutique) et Francis (fin de journée), des Mannequins avec skin de joueur.
 * Chaque PNJ porte une clé PDC propre au plugin ("role:arène") : aucun autre plugin n'y touche
 * et on ne touche à aucune entité qui n'est pas la nôtre.
 */
public final class NpcManager {

    public static final String ROLE_GERTRUDE = "gertrude";
    public static final String ROLE_FRANCIS = "francis";

    private final CleanFishingPlugin plugin;
    private final NamespacedKey npcKey;
    private final Set<UUID> active = new HashSet<>();

    public NpcManager(CleanFishingPlugin plugin) {
        this.plugin = plugin;
        this.npcKey = new NamespacedKey(plugin, "npc");
    }

    public NamespacedKey key() {
        return npcKey;
    }

    /** Fait apparaître les deux PNJ de l'arène et renvoie leurs identifiants. */
    public List<UUID> spawn(Arena arena) {
        List<UUID> ids = new ArrayList<>();
        ids.add(spawnOne(arena.gertrude(), ROLE_GERTRUDE, arena.name()));
        ids.add(spawnOne(arena.francis(), ROLE_FRANCIS, arena.name()));
        ids.removeIf(java.util.Objects::isNull);
        return ids;
    }

    private UUID spawnOne(Location loc, String role, String arenaName) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("npc." + role);
        String name = cfg != null ? cfg.getString("name", role) : role;
        String description = cfg != null ? cfg.getString("description", "") : "";
        ResolvableProfile profile = profileFor(cfg);

        World world = loc.getWorld();
        Mannequin npc = world.spawn(loc, Mannequin.class, m -> {
            m.setPersistent(false);
            m.setImmovable(true);
            m.setInvulnerable(true);
            m.setSilent(true);
            m.setRemoveWhenFarAway(false);
            m.customName(Text.mm("<gold><bold>" + Text.esc(name)));
            m.setCustomNameVisible(true);
            m.setDescription(description.isBlank() ? null : Text.mm("<gray>" + Text.esc(description)));
            m.setProfile(profile);
            m.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, role + ":" + arenaName);
        });
        active.add(npc.getUniqueId());
        return npc.getUniqueId();
    }

    private ResolvableProfile profileFor(ConfigurationSection cfg) {
        if (cfg == null) {
            return Mannequin.defaultProfile();
        }
        String texture = cfg.getString("skin-texture", "");
        String signature = cfg.getString("skin-signature", "");
        String skinName = cfg.getString("skin-name", "");
        try {
            if (texture != null && !texture.isBlank()) {
                ResolvableProfile.Builder builder = ResolvableProfile.resolvableProfile();
                builder.addProperty(new ProfileProperty("textures", texture,
                        signature == null || signature.isBlank() ? null : signature));
                return builder.build();
            }
            if (skinName != null && !skinName.isBlank()) {
                return ResolvableProfile.resolvableProfile().name(skinName).build();
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Skin de PNJ invalide, skin par défaut utilisé : " + ex.getMessage());
        }
        return Mannequin.defaultProfile();
    }

    public void remove(Iterable<UUID> ids) {
        for (UUID id : ids) {
            active.remove(id);
            Entity entity = org.bukkit.Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    public boolean isOurs(Entity entity) {
        return entity.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING);
    }

    public boolean isActive(Entity entity) {
        return active.contains(entity.getUniqueId());
    }

    /** Supprime les PNJ orphelins (reste d'un crash) qui ne correspondent à aucune partie en cours. */
    public void removeOrphans(Iterable<? extends Entity> entities) {
        for (Entity entity : entities) {
            if (entity instanceof Mannequin && isOurs(entity) && !isActive(entity)) {
                entity.remove();
            }
        }
    }
}
