package fr.cleangang.cleanfishing.listener;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.game.Game;
import fr.cleangang.cleanfishing.npc.NpcManager;
import fr.cleangang.cleanfishing.util.Text;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Interactions avec Gertrude et Francis, et nettoyage des PNJ orphelins.
 * <p>
 * Selon l'entité, le client envoie "interact", "interact_at" ou ni l'un ni l'autre (le clic retombe alors sur
 * l'utilisation de l'objet en main). On écoute donc les trois chemins ; un anti-doublon évite qu'un même clic
 * soit traité plusieurs fois (ce qui ouvrirait deux fois la boutique ou annulerait un vote chez Francis).
 */
public final class NpcListener implements Listener {

    private static final long DEBOUNCE_MS = 250L;
    private static final int REACH = 5;

    private final CleanFishingPlugin plugin;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public NpcListener(CleanFishingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        click(event, "interact");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        click(event, "interact_at");
    }

    private void click(PlayerInteractEntityEvent event, String source) {
        Entity entity = event.getRightClicked();
        if (!plugin.npcs().isOurs(entity)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        handle(event.getPlayer(), entity, source);
    }

    /** Filet de sécurité : le clic n'a touché aucune interaction d'entité mais le joueur visait bien un PNJ. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (plugin.games().of(player) == null) {
            return;
        }
        Entity target = player.getTargetEntity(REACH);
        if (target == null || !plugin.npcs().isOurs(target)) {
            return;
        }
        event.setCancelled(true); // ne lance pas la canne en visant un PNJ
        handle(player, target, "use");
    }

    private void handle(Player player, Entity entity, String source) {
        String tag = entity.getPersistentDataContainer().get(plugin.npcs().key(), PersistentDataType.STRING);
        if (tag == null) {
            return;
        }
        if (plugin.getConfig().getBoolean("debug.npc-clicks", false)) {
            plugin.getLogger().info("[debug] clic PNJ (" + source + ") par " + player.getName() + " -> " + tag);
        }
        long now = System.currentTimeMillis();
        Long previous = lastClick.put(player.getUniqueId(), now);
        if (previous != null && now - previous < DEBOUNCE_MS) {
            return;
        }
        Game game = plugin.games().of(player);
        if (game == null) {
            player.sendMessage(Text.msg("Rejoins une partie avec <yellow>/cfish join<gray> pour parler à ce pêcheur."));
            return;
        }
        String[] parts = tag.split(":", 2);
        if (parts.length < 2 || !parts[1].equalsIgnoreCase(game.arena().name())) {
            return;
        }
        switch (parts[0]) {
            case NpcManager.ROLE_GERTRUDE -> game.openShop(player);
            case NpcManager.ROLE_FRANCIS -> game.openFrancis(player);
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (plugin.npcs().isOurs(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        plugin.npcs().removeOrphans(event.getEntities());
    }
}
