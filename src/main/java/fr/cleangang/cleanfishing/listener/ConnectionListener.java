package fr.cleangang.cleanfishing.listener;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.game.Game;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Gère les déconnexions en pleine partie et les restaurations après un crash. */
public final class ConnectionListener implements Listener {

    private final CleanFishingPlugin plugin;

    public ConnectionListener(CleanFishingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Game game = plugin.games().of(player);
        if (game != null) {
            game.leave(player, true);
        }
        plugin.codex().unload(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.states().hasPending(player.getUniqueId()) || plugin.games().of(player) != null) {
            return;
        }
        // état sauvegardé mais aucune partie : le serveur a redémarré ou le joueur a crashé en jeu
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && plugin.games().of(player) == null) {
                plugin.states().restore(player);
            }
        }, 10L);
    }
}
