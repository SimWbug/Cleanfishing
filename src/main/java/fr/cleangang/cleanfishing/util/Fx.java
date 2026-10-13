package fr.cleangang.cleanfishing.util;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/**
 * Effets sonores et particules via des clés texte : on évite les constantes d'enum
 * (qui changent de nom d'une version à l'autre) ; une clé inconnue est simplement ignorée.
 */
public final class Fx {

    private Fx() {
    }

    public static void sound(Location loc, String key, float volume, float pitch) {
        if (loc.getWorld() == null) {
            return;
        }
        loc.getWorld().playSound(loc, key, SoundCategory.PLAYERS, volume, pitch);
    }

    public static void sound(Player player, String key, float volume, float pitch) {
        player.playSound(player.getLocation(), key, SoundCategory.PLAYERS, volume, pitch);
    }

    /** Son entendu par ce joueur seulement, joué à une position donnée. */
    public static void soundFor(Player player, Location at, String key, float volume, float pitch) {
        player.playSound(at, key, SoundCategory.AMBIENT, volume, pitch);
    }

    /** Musique (catégorie Musique : réglable par le joueur dans ses options de son). */
    public static void music(Player player, String key, float volume) {
        player.playSound(player.getLocation(), key, SoundCategory.MUSIC, volume, 1f);
    }

    public static void stopMusic(Player player, String key) {
        player.stopSound(key, SoundCategory.MUSIC);
    }

    public static void particle(Location loc, String key, int count, double dx, double dy, double dz, double speed) {
        if (loc.getWorld() == null) {
            return;
        }
        Particle particle = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft(key));
        if (particle == null) {
            return;
        }
        loc.getWorld().spawnParticle(particle, loc, count, dx, dy, dz, speed);
    }
}
