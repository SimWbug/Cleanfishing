package fr.cleangang.cleanfishing.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Une arène : l'île de pêche avec ses points clés.
 * Les points sont stockés avec le nom du monde et résolus à la demande, ce qui évite tout problème
 * si un plugin multimonde charge le monde après CleanFishing.
 */
public final class Arena {

    public record Pos(String world, double x, double y, double z, float yaw, float pitch) {

        public static Pos of(Location loc) {
            return new Pos(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        }

        /** Null si le monde n'est pas (encore) chargé. */
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }

    private final String name;
    private Pos spawn;
    private Pos lobby;
    private Pos gertrude;
    private Pos francis;

    public Arena(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    private static Location resolve(Pos pos) {
        return pos == null ? null : pos.toLocation();
    }

    public Location spawn() {
        return resolve(spawn);
    }

    /** Point d'attente avant la partie ; retombe sur le spawn s'il n'est pas défini. */
    public Location lobby() {
        Location loc = resolve(lobby);
        return loc != null ? loc : spawn();
    }

    public Location gertrude() {
        return resolve(gertrude);
    }

    public Location francis() {
        return resolve(francis);
    }

    public void spawn(Location loc) {
        this.spawn = Pos.of(loc);
    }

    public void lobby(Location loc) {
        this.lobby = Pos.of(loc);
    }

    public void gertrude(Location loc) {
        this.gertrude = Pos.of(loc);
    }

    public void francis(Location loc) {
        this.francis = Pos.of(loc);
    }

    public Pos spawnPos() {
        return spawn;
    }

    public Pos lobbyPos() {
        return lobby;
    }

    public Pos gertrudePos() {
        return gertrude;
    }

    public Pos francisPos() {
        return francis;
    }

    public void setPositions(Pos spawn, Pos lobby, Pos gertrude, Pos francis) {
        this.spawn = spawn;
        this.lobby = lobby;
        this.gertrude = gertrude;
        this.francis = francis;
    }

    /** Points obligatoires non définis. */
    public List<String> missing() {
        List<String> missing = new ArrayList<>();
        if (spawn == null) {
            missing.add("spawn");
        }
        if (gertrude == null) {
            missing.add("gertrude");
        }
        if (francis == null) {
            missing.add("francis");
        }
        return missing;
    }

    /** Prête = tous les points définis ET leurs mondes chargés. */
    public boolean isReady() {
        return missing().isEmpty() && spawn() != null && gertrude() != null && francis() != null;
    }
}
