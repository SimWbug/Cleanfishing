package fr.cleangang.cleanfishing.listener;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.game.Game;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

/** Remplace la pêche vanilla par la pêche CleanFishing, uniquement pour les joueurs en partie. */
public final class FishingListener implements Listener {

    private final CleanFishingPlugin plugin;

    public FishingListener(CleanFishingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        Game game = plugin.games().of(event.getPlayer());
        if (game == null) {
            return; // joueur hors partie : on ne touche à rien (FindThePoulet et le reste du serveur restent intacts)
        }
        switch (event.getState()) {
            case FISHING -> {
                if (!game.canFish()) {
                    event.setCancelled(true);
                    return;
                }
                game.onCast(event.getPlayer(), event.getHook());
            }
            case CAUGHT_FISH -> {
                event.setExpToDrop(0);
                Entity caught = event.getCaught();
                if (caught != null) {
                    caught.remove();
                }
                game.onCatch(event.getPlayer(), event.getHook().getLocation());
            }
            case CAUGHT_ENTITY -> event.setCancelled(true);
            default -> {
            }
        }
    }
}
