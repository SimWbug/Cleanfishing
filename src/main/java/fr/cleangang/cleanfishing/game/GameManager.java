package fr.cleangang.cleanfishing.game;

import fr.cleangang.cleanfishing.CleanFishingPlugin;
import fr.cleangang.cleanfishing.arena.Arena;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Registre des parties en cours : une partie par arène, un joueur dans une seule partie. */
public final class GameManager {

    private final CleanFishingPlugin plugin;
    private final Map<String, Game> byArena = new HashMap<>();
    private final Map<UUID, Game> byPlayer = new HashMap<>();

    public GameManager(CleanFishingPlugin plugin) {
        this.plugin = plugin;
    }

    public Game of(Player player) {
        return byPlayer.get(player.getUniqueId());
    }

    public Game forArena(String arenaName) {
        return byArena.get(arenaName.toLowerCase());
    }

    public Game getOrCreate(Arena arena) {
        return byArena.computeIfAbsent(arena.name().toLowerCase(), k -> new Game(plugin, arena));
    }

    void bind(UUID player, Game game) {
        byPlayer.put(player, game);
    }

    void unbind(UUID player) {
        byPlayer.remove(player);
    }

    void remove(Game game) {
        byArena.remove(game.arena().name().toLowerCase(), game);
        byPlayer.values().removeIf(g -> g == game);
    }

    public List<Game> all() {
        return new ArrayList<>(byArena.values());
    }

    /** Arrête toutes les parties (arrêt du plugin) en rendant leurs affaires aux joueurs. */
    public void shutdown() {
        for (Game game : all()) {
            game.stop("Le plugin est en cours d'arrêt.");
        }
    }
}
