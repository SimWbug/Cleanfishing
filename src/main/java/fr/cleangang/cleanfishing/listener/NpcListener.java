package fr.cleangang.cleanfishing.listener;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.game.Game;
import fr.cleangang.cleanfishing.npc.NpcManager;
import fr.cleangang.cleanfishing.util.Text;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

/** Interactions avec Gertrude et Francis, et nettoyage des PNJ orphelins. */
public final class NpcListener implements Listener {

    private final CleanFishingPlugin plugin;

    public NpcListener(CleanFishingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event instanceof PlayerInteractAtEntityEvent) {
            return; // Paper envoie les deux événements : on ne traite que le clic "entité"
        }
        Entity entity = event.getRightClicked();
        String tag = entity.getPersistentDataContainer().get(plugin.npcs().key(), PersistentDataType.STRING);
        if (tag == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
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
            case NpcManager.ROLE_FRANCIS -> game.francis(player);
            default -> {
            }
        }
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
