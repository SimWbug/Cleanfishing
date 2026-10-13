package fr.cleangang.cleanfishing.listener;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import org.bukkit.entity.Enemy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

/**
 * Empêche les monstres hostiles d'apparaître dans la zone de jeu d'une arène (rayon autour du spawn).
 * Les apparitions voulues par un admin ou un autre plugin (commande, oeuf, spawn "custom") ne sont pas touchées,
 * et rien d'autre que les monstres hostiles n'est concerné (animaux, villageois, PNJ...).
 */
public final class ZoneListener implements Listener {

    private final CleanFishingPlugin plugin;

    public ZoneListener(CleanFishingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Enemy)) {
            return;
        }
        SpawnReason reason = event.getSpawnReason();
        if (reason == SpawnReason.CUSTOM || reason == SpawnReason.COMMAND
                || reason == SpawnReason.SPAWNER_EGG || reason == SpawnReason.DISPENSE_EGG) {
            return;
        }
        if (plugin.inProtectedZone(event.getLocation())) {
            event.setCancelled(true);
        }
    }
}
