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

    /** Raison pour laquelle l'arène ne peut pas être rejointe maintenant, ou null si elle le peut. */
    public String joinError(Arena arena) {
        if (!arena.isReady()) {
            return "Cette arène n'est pas prête (points manquants ou monde non chargé).";
        }
        Game game = forArena(arena.name());
        if (game != null) {
            if (game.state() != Game.State.LOBBY) {
                return "Une partie est déjà en cours dans cette arène.";
            }
            if (game.playerCount() >= plugin.getConfig().getInt("game.max-players", 8)) {
                return "Cette arène est pleine.";
            }
        }
        return null;
    }

    /** Fait rejoindre l'arène au joueur. Renvoie un message d'erreur, ou null si c'est réussi. */
    public String tryJoin(Player player, Arena arena) {
        if (!player.hasPermission("cleanfishing.play")) {
            return "Tu n'as pas la permission.";
        }
        if (of(player) != null) {
            return "Tu es déjà dans une partie.";
        }
        if (plugin.states().hasPending(player.getUniqueId())) {
            return "Tes affaires d'avant-partie sont en cours de restauration, réessaie dans un instant.";
        }
        String error = joinError(arena);
        if (error != null) {
            return error;
        }
        Game game = getOrCreate(arena);
        if (!game.join(player)) {
            game.discardIfEmpty();
            return "Impossible de rejoindre : la partie est pleine ou a déjà commencé.";
        }
        return null;
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
